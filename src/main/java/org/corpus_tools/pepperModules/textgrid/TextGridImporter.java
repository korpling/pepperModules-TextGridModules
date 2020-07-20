package org.corpus_tools.pepperModules.textgrid;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import org.corpus_tools.pepper.common.DOCUMENT_STATUS;
import org.corpus_tools.pepper.common.PepperConfiguration;
import org.corpus_tools.pepper.impl.PepperImporterImpl;
import org.corpus_tools.pepper.impl.PepperMapperImpl;
import org.corpus_tools.pepper.modules.PepperImporter;
import org.corpus_tools.pepper.modules.PepperMapper;
import org.corpus_tools.pepper.modules.PepperModule;
import org.corpus_tools.pepper.modules.PepperModuleProperties;
import org.corpus_tools.pepper.modules.exceptions.PepperModuleNotReadyException;
import org.corpus_tools.salt.SALT_TYPE;
import org.corpus_tools.salt.SaltFactory;
import org.corpus_tools.salt.common.SCorpus;
import org.corpus_tools.salt.common.SDocument;
import org.corpus_tools.salt.common.SMedialDS;
import org.corpus_tools.salt.common.SMedialRelation;
import org.corpus_tools.salt.common.SSpan;
import org.corpus_tools.salt.common.STextualDS;
import org.corpus_tools.salt.common.STimeline;
import org.corpus_tools.salt.common.STimelineRelation;
import org.corpus_tools.salt.common.SToken;
import org.corpus_tools.salt.core.SRelation;
import org.corpus_tools.salt.graph.Identifier;
import org.corpus_tools.salt.util.DataSourceSequence;
import org.eclipse.emf.common.util.URI;
import org.osgi.service.component.annotations.Component;
import org.praat.Interval;
import org.praat.IntervalTier;
import org.praat.Point;
import org.praat.PraatFile;
import org.praat.PraatObject;
import org.praat.TextGrid;
import org.praat.TextTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/**
 * Thomas Krause <krauseto@hu-berlin.de>
 */
@Component(name = "TextGridImporterComponent", factory = "PepperImporterComponentFactory")
public class TextGridImporter extends PepperImporterImpl implements PepperImporter {
	/**
	 * this is a logger, for recording messages during program process, like debug
	 * messages
	 **/
	private static final Logger log = LoggerFactory.getLogger(TextGridImporter.class);

	/**
	 * <strong>OVERRIDE THIS METHOD FOR CUSTOMIZATION</strong> <br/>
	 * A constructor for your module. Set the coordinates, with which your module
	 * shall be registered. The coordinates (modules name, version and supported
	 * formats) are a kind of a fingerprint, which should make your module unique.
	 */
	public TextGridImporter() {
		super();
		setName("TextGridImporter");
		setSupplierContact(URI.createURI(PepperConfiguration.EMAIL));
		setSupplierHomepage(URI.createURI(PepperConfiguration.HOMEPAGE));
		setDesc("Imports TextGrid files from the Praat tool.");
		addSupportedFormat("TextGrid", "1.0", null);
		getDocumentEndings().add("TextGrid");
		getDocumentEndings().add("textGrid");
		setProperties(new TextGridImporterProperties());
	}

	/**
	 * 
	 * @param Identifier {@link Identifier} of the {@link SCorpus} or
	 *                   {@link SDocument} to be processed.
	 * @return {@link PepperMapper} object to do the mapping task for object
	 *         connected to given {@link Identifier}
	 */
	public PepperMapper createPepperMapper(Identifier Identifier) {
		TextGridMapper mapper = new TextGridMapper();
		/**
		 * TODO Set the exact resource, which should be processed by the created mapper
		 * object, if the default mechanism of importCorpusStructure() was used, the
		 * resource could be retrieved by getIdentifier2ResourceTable().get(Identifier),
		 * just uncomment this line
		 */
		// mapper.setResourceURI(getIdentifier2ResourceTable().get(Identifier));
		return (mapper);
	}

	/**
	 * 
	 * @author Thomas Krause <krauseto@hu-berlin.de>
	 *
	 */
	public static class TextGridMapper extends PepperMapperImpl {

		@Override
		public DOCUMENT_STATUS mapSCorpus() {
			// getScorpus() returns the current corpus object.

			// TODO: check if TextGrid supports meta-data
			return (DOCUMENT_STATUS.COMPLETED);
		}

		private Map<Double, Integer> mapTimeline(TextGrid grid) {

			// iterate over all tiers to find the points of time for the timeline and sort
			// them
			TreeSet<Double> pots = new TreeSet<>();
			for (PraatObject gridObject : grid) {
				if (gridObject instanceof TextTier) {
					TextTier textTier = (TextTier) gridObject;
					for (Point p : textTier) {
						pots.add(p.getTime());
					}
				} else if (gridObject instanceof IntervalTier) {
					IntervalTier tier = (IntervalTier) gridObject;
					for (Interval i : tier) {
						// add both times
						pots.add(i.getStartTime());
						pots.add(i.getEndTime());
					}
				}
			}
			STimeline timeline = SaltFactory.createSTimeline();
			getDocument().getDocumentGraph().setTimeline(timeline);
			Map<Double, Integer> time2pot = new LinkedHashMap<>();
			for (double timePoint : pots) {
				timeline.increasePointOfTime();
				time2pot.put(timePoint, timeline.getEnd());
			}
			return time2pot;
		}

		private void mapTokens(TextGrid grid, SMedialDS mediaFile, Map<Double, Integer> time2pot) {
			Map<String, String> anno2prim = getProperties().getAnnoPrimRel();
			Set<String> annoTiers = anno2prim.keySet();
			Set<String> primaryTiers = new HashSet<>(anno2prim.values());


			for (PraatObject gridObject : grid) {
				if (gridObject instanceof IntervalTier) {
					IntervalTier tier = (IntervalTier) gridObject;
					if (primaryTiers.contains(tier.getName())
							|| (getProperties().isMapUnknownAsToken() && !annoTiers.contains(tier.getName()))) {
						StringBuilder text = new StringBuilder();
						STextualDS primaryText = getDocument().getDocumentGraph().createTextualDS(text.toString());
						primaryText.setName(tier.getName());

						ListIterator<Interval> intervals = tier.iterator();
						while (intervals.hasNext()) {
							Interval tokInterval = intervals.next();
							if(!tokInterval.getText().isEmpty()) {
								int tokStart = text.length();
								text.append(tokInterval.getText());
								int tokEnd = text.length();
								if (intervals.hasNext()) {
									text.append(' ');
								}
	
								SToken tok = getDocument().getDocumentGraph().createToken(primaryText, tokStart, tokEnd);
	
								if(mediaFile != null) {
									// map time of interval to media file
									SMedialRelation mediaRel = SaltFactory.createSMedialRelation();
									mediaRel.setSource(tok);
									mediaRel.setTarget(mediaFile);
									mediaRel.setStart(tokInterval.getStartTime());
									mediaRel.setEnd(tokInterval.getEndTime());
		
									getDocument().getDocumentGraph().addRelation(mediaRel);
								}
	
								// map to point in time on timeline
								Integer potStart = time2pot.get(tokInterval.getStartTime());
								Integer potEnd = time2pot.get(tokInterval.getEndTime());
								if (potStart != null && potEnd != null) {
									STimelineRelation timeRel = SaltFactory.createSTimelineRelation();
									timeRel.setSource(tok);
									timeRel.setTarget(getDocument().getDocumentGraph().getTimeline());
									timeRel.setStart(potStart);
									timeRel.setEnd(potEnd);
	
									getDocument().getDocumentGraph().addRelation(timeRel);
								} else {
									log.warn(
											"{}: Could not find overlapped point in time for span {}={} with interval {}-{}",
											getDocument().getName(), tier.getName(), tokInterval.getText(),
											String.format("", tokInterval.getStartTime(), tokInterval.getEndTime()));
								}
							}
						}
						primaryText.setText(text.toString());
					}
				}
			}
		}

		private void mapSpans(TextGrid grid, Map<Double, Integer> time2pot) {
			Map<String, String> annoPrimRel = getProperties().getAnnoPrimRel();

			for (PraatObject gridObject : grid) {
				if (gridObject instanceof IntervalTier) {
					IntervalTier tier = (IntervalTier) gridObject;
					boolean searchTokens = ((TextGridImporterProperties)getProperties()).searchTokens();
					String prim = annoPrimRel.get(tier.getName());
					if (prim != null) {
						ListIterator<Interval> intervals = tier.iterator();
						while (intervals.hasNext()) {
							Interval spanInterval = intervals.next();
							Integer potStart = time2pot.get(spanInterval.getStartTime());
							Integer potEnd = time2pot.get(spanInterval.getEndTime());
							if (spanInterval.getText() != null && !spanInterval.getText().isEmpty() && potStart != null
									&& potEnd != null) {
								// find matching tokens for the interval
								DataSourceSequence<Integer> seq = new DataSourceSequence<>();
								seq.setDataSource(getDocument().getDocumentGraph().getTimeline());
								seq.setStart(potStart);
								seq.setEnd(potEnd);

								List<SToken> allOverlappedToken = getDocument().getDocumentGraph()
										.getTokensBySequence(seq);
								List<SToken> filteredOverlappedToken = new ArrayList<>(allOverlappedToken.size());

								for (SToken t : allOverlappedToken) {
									List<DataSourceSequence> overlappedDS = getDocument().getDocumentGraph()
											.getOverlappedDataSourceSequence(t, SALT_TYPE.STEXT_OVERLAPPING_RELATION);
									if (overlappedDS != null && !overlappedDS.isEmpty()) {
										for (DataSourceSequence textSeq : overlappedDS) {
											if (textSeq.getDataSource() instanceof STextualDS) {
												STextualDS ds = (STextualDS) textSeq.getDataSource();
												if (prim.equals(ds.getName())) {
													filteredOverlappedToken.add(t);
												} else if (searchTokens) {
													Iterator<STextualDS> itDS = getDocument().getDocumentGraph().getTextualDSs().iterator();
													STextualDS targetDS = null;
													while ((targetDS == null || !prim.equals(targetDS.getName())) && itDS.hasNext()) {
														targetDS = itDS.next();
													}
													if (targetDS != null && prim.equals(targetDS.getName())) {
														Iterator<SToken> potentialTargetTokens = getDocument().getDocumentGraph().getSortedTokenByText(
																getDocument().getDocumentGraph().getTokensBySequence(new DataSourceSequence<Number>(targetDS, 0, targetDS.getText().length()))).iterator();
														List<SToken> annoTargets = new ArrayList<>();
														SToken tok = null;
														STimelineRelation tRel = null;
														int currStart = 0;
														int currEnd = 0;
														while (potentialTargetTokens.hasNext() && currStart < potEnd) {
															tok = potentialTargetTokens.next();
															tRel = (STimelineRelation) tok.getOutRelations().stream().filter(new Predicate<SRelation>() {
																@Override
																public boolean test(SRelation r) {
																	return r instanceof STimelineRelation;
																}
															}).findFirst().get(); 
															currStart = tRel.getStart();
															currEnd = tRel.getEnd();
															if (currStart >= potStart && currStart < potEnd && currEnd <= potEnd) {
																annoTargets.add(tok);
															}															
														}
														if (log.isDebugEnabled()) {
															log.debug("[" + prim + "|" + tier.getName() + "]" + "Searching: " + potStart + " to " + potEnd + ", found " + annoTargets.size() + " tokens.");
														}
														filteredOverlappedToken.addAll(annoTargets);
													}
												}
											} 
										}
									} 
								}

								if (filteredOverlappedToken.isEmpty()) {
									if (prim != null && !prim.isEmpty()) {
										log.warn(
												"{}: Could not find overlapped token for span {}=\"{}\" on primary text {} "
														+ "(interval {})",
												getDocument().getName(), tier.getName(), spanInterval.getText(), prim,
												String.format("%.2f-%.2f", spanInterval.getStartTime(),
														spanInterval.getEndTime()));
									} else {
										log.warn(
												"{}: Could not find overlapped token for span {}=\"{}\" "
														+ "(interval {})",
												getDocument().getName(), tier.getName(), spanInterval.getText(),
												String.format("%.2f-%.2f", spanInterval.getStartTime(),
														spanInterval.getEndTime()));
									}
								} else {
									if (spanInterval.getText() != null && !spanInterval.getText().isEmpty()) {
										SSpan span = getDocument().getDocumentGraph()
												.createSpan(filteredOverlappedToken);
										span.createAnnotation(null, tier.getName(), spanInterval.getText());
									}
								}
							}
						}
					}
				}
			}
		}

		/**
		 * <strong>OVERRIDE THIS METHOD FOR CUSTOMIZATION</strong> <br/>
		 * This is the place for the real work. Here you have to do anything necessary,
		 * to map a corpus to Salt. These could be things like: reading a file, mapping
		 * the content, closing the file, cleaning up and so on. <br/>
		 * In our dummy implementation, we do not read a file, for not making the code
		 * too complex. We just show how to create a simple document-structure in Salt,
		 * in following steps:
		 * <ol>
		 * <li>creating primary data</li> log.warn("Could not find overlapped point in
		 * time for span {}={} ({})", tier.getName(), tokInterval.getText(),
		 * String.format("%.2f -- %.2f", tokInterval.getStartTime(),
		 * tokInterval.getEndTime()));
		 * <li>creating tokenization</li>
		 * <li>creating part-of-speech annotation for tokenization</li>
		 * <li>creating information structure annotation via spans</li>
		 * <li>creating anaphoric relation via pointing relation</li>
		 * <li>creating syntactic annotations</li>
		 * </ol>
		 */
		@Override
		public DOCUMENT_STATUS mapSDocument() {

			// the method getDocument() returns the current document for
			// creating the document-structure
			getDocument().setDocumentGraph(SaltFactory.createSDocumentGraph());
			// to get the exact resource, which be processed now, call
			// getResources(), make sure, it was set in createMapper()
			URI resource = getResourceURI();

			// we record, which file currently is imported to the debug stream,
			// in this dummy implementation the resource is null
			log.debug("Importing the file {}.", resource);

			try {
				PraatObject rootObj = null;
				try {
					rootObj = PraatFile.readFromFile(new File(resource.toFileString()), StandardCharsets.UTF_8);
				} catch (IllegalArgumentException ex) {
					// try again with UTF-16
					rootObj = PraatFile.readFromFile(new File(resource.toFileString()), StandardCharsets.UTF_16);
				}

				if (rootObj instanceof TextGrid) {
					TextGrid grid = (TextGrid) rootObj;
					// link actual file to media file, assume they have the same name but ends with
					// an audio file extension
					String audioExt = getProperties().getAudioExtension();
					File originalFile = new File(getResourceURI().toFileString());
					File mediaFile = new File(originalFile.getParent(),
							Files.getNameWithoutExtension(originalFile.getPath()) + audioExt);
					
					SMedialDS mediaFileDS = null;
					
					if(mediaFile.isFile()) {
						mediaFileDS = SaltFactory.createSMedialDS();
						mediaFileDS.setMediaReference(URI.createFileURI(mediaFile.getAbsolutePath()));
						getDocument().getDocumentGraph().addNode(mediaFileDS);
					}
					

					Map<Double, Integer> time2pot = mapTimeline(grid);
					mapTokens(grid, mediaFileDS, time2pot);
					mapSpans(grid, time2pot);

				}
			} catch (Exception ex) {
				log.error("Could not load Praat TextGrid file", ex);
				return DOCUMENT_STATUS.FAILED;
			}

			// we set progress to 'done' to notify the user about the process
			// status (this is very helpful, especially for longer taking
			// processes)
			setProgress(1.0);

			// now we are done and return the status that everything was
			// successful
			return (DOCUMENT_STATUS.COMPLETED);
		}

		@Override
		public TextGridImporterProperties getProperties() {
			return (TextGridImporterProperties) super.getProperties();
		}
	}

	/**
	 * <strong>OVERRIDE THIS METHOD FOR CUSTOMIZATION</strong> <br/>
	 * This method is called by the pepper framework and returns if a corpus located
	 * at the given {@link URI} is importable by this importer. If yes, 1 must be
	 * returned, if no 0 must be returned. If it is not quite sure, if the given
	 * corpus is importable by this importer any value between 0 and 1 can be
	 * returned. If this method is not overridden, null is returned.
	 * 
	 * @return 1 if corpus is importable, 0 if corpus is not importable, 0 < X < 1,
	 *         if no definitive answer is possible, null if method is not overridden
	 */
	public Double isImportable(URI corpusPath) {
		// TODO some code to analyze the given corpus-structure
		return (null);
	}

	// =================================================== optional
	// ===================================================
	/**
	 * <strong>OVERRIDE THIS METHOD FOR CUSTOMIZATION</strong> <br/>
	 * This method is called by the pepper framework after initializing this object
	 * and directly before start processing. Initializing means setting properties
	 * {@link PepperModuleProperties}, setting temporary files, resources etc.
	 * returns false or throws an exception in case of {@link PepperModule} instance
	 * is not ready for any reason. <br/>
	 * So if there is anything to do, before your importer can start working, do it
	 * here.
	 * 
	 * @return false, {@link PepperModule} instance is not ready for any reason,
	 *         true, else.
	 */
	@Override
	public boolean isReadyToStart() throws PepperModuleNotReadyException {
		// TODO make some initializations if necessary
		return (super.isReadyToStart());
	}
}
