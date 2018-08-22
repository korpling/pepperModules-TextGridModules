package org.corpus_tools.pepperModules.textgrid;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.corpus_tools.pepper.common.DOCUMENT_STATUS;
import org.corpus_tools.pepper.common.PepperConfiguration;
import org.corpus_tools.pepper.impl.PepperImporterImpl;
import org.corpus_tools.pepper.impl.PepperMapperImpl;
import org.corpus_tools.pepper.modules.PepperImporter;
import org.corpus_tools.pepper.modules.PepperMapper;
import org.corpus_tools.pepper.modules.PepperModule;
import org.corpus_tools.pepper.modules.PepperModuleProperties;
import org.corpus_tools.pepper.modules.exceptions.PepperModuleNotReadyException;
import org.corpus_tools.salt.SaltFactory;
import org.corpus_tools.salt.common.SCorpus;
import org.corpus_tools.salt.common.SDocument;
import org.corpus_tools.salt.common.SMedialDS;
import org.corpus_tools.salt.common.SMedialRelation;
import org.corpus_tools.salt.common.STextualDS;
import org.corpus_tools.salt.common.STimeline;
import org.corpus_tools.salt.common.SToken;
import org.corpus_tools.salt.graph.Identifier;
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

/**
 * This is a dummy implementation of a {@link PepperImporter}, which can be used
 * as a template to create your own module from. The current implementation
 * creates a corpus-structure looking like this:
 * 
 * <pre>
 *       c1
 *    /      \
 *   c2      c3
 *  /  \    /  \
 * d1  d2  d3  d4
 * </pre>
 * 
 * For each document d1, d2, d3 and d4 the same document-structure is created.
 * The document-structure contains the following structure and annotations:
 * <ol>
 * <li>primary data</li>
 * <li>tokenization</li>
 * <li>part-of-speech annotation for tokenization</li>
 * <li>information structure annotation via spans</li>
 * <li>anaphoric relation via pointing relation</li>
 * <li>syntactic annotations</li>
 * </ol>
 * This dummy implementation is supposed to give you an impression, of how
 * Pepper works and how you can create your own implementation along that dummy.
 * It further shows some basics of creating a simple Salt model. <br/>
 * <strong>This code contains a lot of TODO's. Please have a look at them and
 * adapt the code for your needs </strong> At least, a list of not used but
 * helpful methods:
 * <ul>
 * <li>the salt model to fill can be accessed via {@link #getSaltProject()}</li>
 * <li>customization properties can be accessed via {@link #getProperties()}
 * </li>
 * <li>a place where resources of this bundle are, can be accessed via
 * {@link #getResources()}</li>
 * </ul>
 * If this is the first time, you are implementing a Pepper module, we strongly
 * recommend, to take a look into the 'Developer's Guide for Pepper modules',
 * you will find on
 * <a href="http://corpus-tools.org/pepper/">http://corpus-tools.org/pepper</a>.
 * 
 * @author Thomas Krause
 */
@Component(name = "TextGridImporterComponent", factory = "PepperImporterComponentFactory")
public class TextGridImporter extends PepperImporterImpl implements PepperImporter{
	/** this is a logger, for recording messages during program process, like debug messages**/
	private static final Logger log = LoggerFactory.getLogger(TextGridImporter.class);

	/**
	 * <strong>OVERRIDE THIS METHOD FOR CUSTOMIZATION</strong> <br/>
	 * A constructor for your module. Set the coordinates, with which your
	 * module shall be registered. The coordinates (modules name, version and
	 * supported formats) are a kind of a fingerprint, which should make your
	 * module unique.
	 */
	public TextGridImporter() {
		super();
		setName("TextGridImporter");
		setSupplierContact(URI.createURI(PepperConfiguration.EMAIL));
		setSupplierHomepage(URI.createURI(PepperConfiguration.HOMEPAGE));
		setDesc("Imports TextGrid files from the Praat tool.");
		addSupportedFormat("sample", "1.0", null);
		getDocumentEndings().add(".TextGrid");
		setProperties(new TextGridImporterProperties());
	}
	

	/**
	 * 
	 * @param Identifier
	 *            {@link Identifier} of the {@link SCorpus} or {@link SDocument}
	 *            to be processed.
	 * @return {@link PepperMapper} object to do the mapping task for object
	 *         connected to given {@link Identifier}
	 */
	public PepperMapper createPepperMapper(Identifier Identifier) {
		TextGridMapper mapper = new TextGridMapper();
		/**
		 * TODO Set the exact resource, which should be processed by the created
		 * mapper object, if the default mechanism of importCorpusStructure()
		 * was used, the resource could be retrieved by
		 * getIdentifier2ResourceTable().get(Identifier), just uncomment this
		 * line
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
	
		/**
		 * <strong>OVERRIDE THIS METHOD FOR CUSTOMIZATION</strong> <br/>
		 * If you need to make any adaptations to the corpora like adding
		 * further meta-annotation, do it here. When whatever you have done
		 * successful, return the status {@link DOCUMENT_STATUS#COMPLETED}. If
		 * anything went wrong return the status {@link DOCUMENT_STATUS#FAILED}.
		 * <br/>
		 * In our dummy implementation, we just add a creation date to each
		 * corpus.
		 */
		@Override
		public DOCUMENT_STATUS mapSCorpus() {
			// getScorpus() returns the current corpus object.

			// TODO: check if TextGrid supports meta-data
			return (DOCUMENT_STATUS.COMPLETED);
		}
		
		private Map<Integer, Double> mapTimeline(TextGrid grid) {
			SMedialDS mediaSource = SaltFactory.createSMedialDS();
			getDocument().getDocumentGraph().addNode(mediaSource);
			
			// iterate over text tier to create the points of time for the timeline
			STimeline timeline = getDocument().getDocumentGraph().createTimeline();
			Map<Integer, Double> pot2time = new LinkedHashMap<>();
			for(PraatObject gridObject : grid) {
				if(gridObject instanceof TextTier) {
					TextTier textTier = (TextTier) gridObject;
					ListIterator<Point> itPoints = textTier.iterator();
					while(itPoints.hasNext()) {
						timeline.increasePointOfTime();
						pot2time.put(timeline.getEnd(), itPoints.next().getTime());
					}
				}
			}
			return pot2time;
		}
		
		private void mapTokens(TextGrid grid) {
			Set<String> primTiers = getProperties().getPrimaryText();
			SMedialDS mediaFile = SaltFactory.createSMedialDS();
			
			getDocument().getDocumentGraph().addNode(mediaFile);
			
			for(PraatObject gridObject : grid) {
				if(gridObject instanceof IntervalTier) {
					IntervalTier tier = (IntervalTier) gridObject;
					
					if(primTiers.contains(tier.getName())) {
						StringBuilder text = new StringBuilder();
						STextualDS primaryText = getDocument().getDocumentGraph().createTextualDS(text.toString());
						primaryText.setName(tier.getName());
						
						ListIterator<Interval> intervals = tier.iterator();
						while(intervals.hasNext()) {
							Interval i = intervals.next();
							int tokStart = text.length();
							text.append(i.getText());
							int tokEnd  = text.length();
							if(intervals.hasNext()) {
								text.append(' ');
							}
							
							SToken tok = getDocument().getDocumentGraph()
									.createToken(primaryText, tokStart, tokEnd);
							
							SMedialRelation mediaRel = SaltFactory.createSMedialRelation();
							mediaRel.setSource(tok);
							mediaRel.setTarget(mediaFile);
							mediaRel.setStart(i.getStartTime());
							mediaRel.setEnd(i.getEndTime());
							
							getDocument().getDocumentGraph().addRelation(mediaRel);

						}
						primaryText.setText(text.toString());
					}		
				}
			}
		}

		/**
		 * <strong>OVERRIDE THIS METHOD FOR CUSTOMIZATION</strong> <br/>
		 * This is the place for the real work. Here you have to do anything
		 * necessary, to map a corpus to Salt. These could be things like:
		 * reading a file, mapping the content, closing the file, cleaning up
		 * and so on. <br/>
		 * In our dummy implementation, we do not read a file, for not making
		 * the code too complex. We just show how to create a simple
		 * document-structure in Salt, in following steps:
		 * <ol>
		 * <li>creating primary data</li>
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
				PraatObject rootObj = PraatFile.readFromFile(new File(resource.toFileString()), StandardCharsets.UTF_8);
			
				if(rootObj instanceof TextGrid) {
					TextGrid grid = (TextGrid) rootObj;
					
					Map<Integer, Double> pot2time = mapTimeline(grid);
					mapTokens(grid);
					

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
	 * This method is called by the pepper framework and returns if a corpus
	 * located at the given {@link URI} is importable by this importer. If yes,
	 * 1 must be returned, if no 0 must be returned. If it is not quite sure, if
	 * the given corpus is importable by this importer any value between 0 and 1
	 * can be returned. If this method is not overridden, null is returned.
	 * 
	 * @return 1 if corpus is importable, 0 if corpus is not importable, 0 < X <
	 *         1, if no definitive answer is possible, null if method is not
	 *         overridden
	 */
	public Double isImportable(URI corpusPath) {
		// TODO some code to analyze the given corpus-structure
		return (null);
	}

	// =================================================== optional
	// ===================================================
	/**
	 * <strong>OVERRIDE THIS METHOD FOR CUSTOMIZATION</strong> <br/>
	 * This method is called by the pepper framework after initializing this
	 * object and directly before start processing. Initializing means setting
	 * properties {@link PepperModuleProperties}, setting temporary files,
	 * resources etc. returns false or throws an exception in case of
	 * {@link PepperModule} instance is not ready for any reason. <br/>
	 * So if there is anything to do, before your importer can start working, do
	 * it here.
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
