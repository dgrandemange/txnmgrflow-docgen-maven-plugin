package fr.dgrandemange.txnmgr.flow.docgen;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.DuplicateRealmException;
import org.jpos.jposext.jposworkflow.model.Graph;
import org.jpos.jposext.jposworkflow.service.IDOTLabelFactory;
import org.jpos.jposext.jposworkflow.service.support.FacadeImpl;
import org.jpos.jposext.jposworkflow.service.support.GraphConverterServiceImpl;
import org.jpos.jposext.jposworkflow.service.support.LabelFactoryVelocityImpl;
import org.jpos.jposext.jposworkflow.service.support.TooltipFactoryVelocityImpl;

import com.developpez.adiguba.shell.ProcessConsumer;
import com.developpez.adiguba.shell.Shell;

/**
 * Transaction manager flow documentation generation task<br>
 * Flow graphs are exported in DOT format<br>
 * If GraphViz is available, DOT files may also be converted to SVG<br>
 * 
 * @goal docgen
 * 
 * @requiresDependencyResolution runtime
 * 
 * @author dgrandemange
 * @See <a
 *      href="http://books.sonatype.com/mvnref-book/reference/writing-plugins.html"
 *      >Maven : writing plugins</a>
 */
public class DocGenMojo extends AbstractMojo {
	// @formatter:off
	
	/**
	 * Build directory<br>
	 * 
	 * @parameter expression="${txnmgr-flow-docgen.buildDirectory}" default-value="${project.build.directory}"
	 */
	private String buildDirectory;

	/**
	 * Doc gen directory name<br>
	 * 
	 * @parameter expression="${txnmgr-flow-docgen.docGenDirName}"
	 */
	private String docGenDirName;
	
	/**
	 * Path of transaction manager configuration file<br>
	 * 
	 * @required
	 * @parameter expression="${txnmgr-flow-docgen.txnmgrConfigPath}"
	 */
	private String txnmgrConfigPath;

	/**
	 * Subflow mode activation indicator<br>
	 * 
	 * @parameter expression="${txnmgr-flow-docgen.subflowMode}" default-value="true"
	 */
	private boolean subflowMode;

	/**
	 * @parameter expression="${txnmgr-flow-docgen.alias}" default-value="${project.name} workflow"
	 */
	private String alias;

	/**
	 * GraphViz dot command path<br>
	 * 
	 * @parameter expression="${txnmgr-flow-docgen.graphVizDotCmdPath}" default-value=""
	 */
	private String graphVizDotCmdPath;
	
	/**
	 * @readonly
	 * @parameter expression="${project.runtimeClasspathElements}"
	 */
	private List<String> runtimeClasspathElements;

	// @formatter:on

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.maven.plugin.AbstractMojo#execute()
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {

		File txMgrConfigFile = new File(this.txnmgrConfigPath);

		if (!txMgrConfigFile.exists()) {
			String errMsg = String.format("'%s' is not a valid path",
					this.txnmgrConfigPath);
			getLog().error(errMsg);
			throw new MojoFailureException(errMsg);
		}

		if (txMgrConfigFile.isDirectory()) {
			String errMsg = String.format("'%s' is not a file",
					this.txnmgrConfigPath);
			getLog().error(errMsg);
			throw new MojoFailureException(errMsg);
		}

		// First, convert configuration to graph(s)
		Map<String, Graph> graphs = new HashMap<String, Graph>();
		try {
			genGraphsFromConfigFile(txMgrConfigFile, graphs);
		} catch (MalformedURLException e) {
			throw new MojoFailureException(e.getMessage());
		}

		if (0 == graphs.size()) {
			getLog().warn(
					"Unable to convert config to graph(s). Reason unknown.");
			return;
		}

		// Check output dir path
		String outputDirPath = this.buildDirectory;
		File outputDir = new File(outputDirPath);

		if (!outputDir.exists()) {
			String errMsg = String.format("'%s' is not a valid path",
					outputDirPath);
			getLog().error(errMsg);
			throw new MojoFailureException(errMsg);
		}

		if (!outputDir.isDirectory()) {
			String errMsg = String.format("'%s' is not a directory",
					outputDirPath);
			getLog().error(errMsg);
			throw new MojoFailureException(errMsg);
		}

		if (!outputDir.canWrite()) {
			String errMsg = String.format("'%s' is not a writeable directory",
					outputDirPath);
			getLog().error(errMsg);
			throw new MojoFailureException(errMsg);
		}

		String docGenDirPath = outputDir.getAbsolutePath() + File.separator
				+ "txnmgrDocGen";
		File docGenDir = new File(docGenDirPath);
		if (!(docGenDir.exists())) {
			if (!(docGenDir.mkdir())) {
				String errMsg = String.format(
						"unable to create directory '%s'", docGenDirPath);
				getLog().error(errMsg);
				throw new MojoFailureException(errMsg);
			}
		}

		if ((this.docGenDirName == null)
				|| (this.docGenDirName.trim().length() == 0)) {
			this.docGenDirName = txMgrConfigFile.getName();
		}
		String subWorkDirPath = docGenDir.getAbsolutePath() + File.separator
				+ this.docGenDirName;
		File subWorkDir = new File(subWorkDirPath);
		if (!(subWorkDir.exists())) {
			if (!(subWorkDir.mkdir())) {
				String errMsg = String.format(
						"unable to create directory '%s'", subWorkDirPath);
				getLog().error(errMsg);
				throw new MojoFailureException(errMsg);
			}
		}

		// Try to export the graph(s) to DOT format
		List<String> dotFiles = new ArrayList<String>();
		int dotCreatedCount = 0;
		for (Entry<String, Graph> entry : graphs.entrySet()) {
			String key = entry.getKey();
			String fileName;
			String graphName;
			if (FacadeImpl.ROOT_KEY.equals(key)) {
				graphName = this.alias;
				fileName = "root.dot";
			} else {
				graphName = key;
				fileName = graphName + ".dot";
			}
			String createdDotFilePath = createDOTFile(entry.getValue(),
					fileName, graphName, subWorkDir.getAbsolutePath());
			dotFiles.add(createdDotFilePath);
			dotCreatedCount++;
			getLog().info(
					String.format("DOT file '%s' created", createdDotFilePath));
		}
		getLog().info(String.format("%d DOT file(s) created", dotCreatedCount));

		convertDOT2SVG(subWorkDir, dotFiles);

		try {
			extractWebSiteTemplate(subWorkDir);
		} catch (IOException e) {
			getLog().error(e.getMessage());
			throw new MojoFailureException(e.getMessage());
		}
	}

	protected void extractWebSiteTemplate(File subWorkDir) throws IOException {
		byte[] buffer = new byte[1024];

		ZipInputStream zis = new ZipInputStream(
				this.getClass()
						.getResourceAsStream(
								"/fr/dgrandemange/txnmgr/flow/docgen/site-template.zip"));

		ZipEntry ze = zis.getNextEntry();
		while (ze != null) {
			String fileName = ze.getName();

			File newFile = new File(subWorkDir.getAbsolutePath()
					+ File.separator + fileName);

			getLog().debug("file unzip : " + newFile.getAbsoluteFile());

			if (ze.isDirectory()) {
				newFile.mkdirs();
			} else {
				// Create all non exists folders
				new File(newFile.getParent()).mkdirs();

				FileOutputStream fos = new FileOutputStream(newFile);

				int len;
				while ((len = zis.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
				}

				fos.close();
			}

			ze = zis.getNextEntry();
		}

		zis.closeEntry();
		zis.close();

	}

	protected void convertDOT2SVG(File subWorkDir, List<String> dotFiles) {
		if (this.graphVizDotCmdPath == null) {
			return;
		}

		for (String dotFilePath : dotFiles) {
			ByteArrayOutputStream bosOut = new ByteArrayOutputStream();
			ByteArrayOutputStream bosErr = new ByteArrayOutputStream();
			Shell sh = new Shell();
			ProcessConsumer processConsumer = sh.exec("\"" + graphVizDotCmdPath
					+ "\"", "-Gcharset=latin1", "-Tsvg", "-O", "\""
					+ dotFilePath + "\"");
			try {
				processConsumer.error(bosErr).output(bosOut).consume();
			} catch (Exception e) {
				getLog().error(e.getMessage());
			}

			try {
				bosOut.close();
				String infoMsg = new String(bosOut.toString());
				if ((infoMsg != null) && (infoMsg.trim().length() > 0)) {
					getLog().info(infoMsg);
				}
			} catch (IOException e) {
			}
			try {
				bosErr.close();
				String errMsg = new String(bosErr.toString());
				if ((errMsg != null) && (errMsg.trim().length() > 0)) {
					getLog().error(errMsg);
				}
			} catch (IOException e) {
			}
		}
	}

	/**
	 * @param txMgrConfigFile
	 * @param graphs
	 * @throws MalformedURLException
	 */
	protected void genGraphsFromConfigFile(File txMgrConfigFile,
			Map<String, Graph> graphs) throws MalformedURLException {
		URL url = txMgrConfigFile.toURI().toURL();
		FacadeImpl jPosWorkflowFacade = new FacadeImpl();

		ClassLoader classLoader = null;
		try {
			classLoader = createClassLoader();
		} catch (Exception e) {
			getLog().warn(e.getMessage());
		}
		ContextMgmtInfoPopulatorMojoImpl ctxMgmtInfoPopulator = new ContextMgmtInfoPopulatorMojoImpl(
				classLoader, getLog());

		if (this.subflowMode) {
			jPosWorkflowFacade.getGraphSubFlowMode(url, ctxMgmtInfoPopulator,
					graphs);
		} else {
			Graph graph = jPosWorkflowFacade
					.getGraph(url, ctxMgmtInfoPopulator);
			graphs.put(FacadeImpl.ROOT_KEY, graph);
		}
	}

	/**
	 * @param graph
	 *            The graph to export as DOT
	 * @param graphName
	 *            Graph name used as DOT file name
	 * @param outputDir
	 *            DOT file output directory
	 */
	protected String createDOTFile(Graph graph, String fileName,
			String graphName, String outputDir) {
		IDOTLabelFactory labelFactory = new LabelFactoryVelocityImpl();
		IDOTLabelFactory toolTipFactory = new TooltipFactoryVelocityImpl();
		GraphConverterServiceImpl graphConverterService = new GraphConverterServiceImpl();
		graphConverterService.setLabelFactory(labelFactory);
		graphConverterService.setToolTipFactory(toolTipFactory);
		FileOutputStream result = null;
		PrintWriter pw = null;
		try {
			String saveFilePath = String.format("%s%s%s", outputDir,
					System.getProperty("file.separator"), fileName);
			result = new FileOutputStream(saveFilePath);
			pw = new PrintWriter(result);
			graphConverterService.convertGraphToDOT(graphName, graph, pw);
			pw.flush();
			pw.close();
			return saveFilePath;
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} finally {
			if (result != null) {
				try {
					result.flush();
					result.close();
				} catch (Exception e) {
				}
			}
		}
	}

	/**
	 * It uses Maven <code>ClassWorld</code> and <code>ClassRealm</code> where
	 * the project classes/resources/dependencies will be associated<br>
	 * See <a href=http://stackoverflow.com/questions/2659048/add-maven-build-
	 * classpath -to-plugin-execution-classpath>add maven build classpath to
	 * plugin execution classpath</a><br>
	 * See also Apache Maven (Pearson editions) chapter 11, p 181 <i>Créer un
	 * plugin : des classes et des royaumes</i>
	 * 
	 * @return
	 * @throws MalformedURLException
	 * @throws DuplicateRealmException
	 */
	protected ClassLoader createClassLoader() throws MalformedURLException,
			DuplicateRealmException {
		ClassWorld world = new ClassWorld();
		ClassRealm realm = world.newRealm("txnmgr-docgen");

		final URL[] urls = buildURLs(runtimeClasspathElements);
		for (URL url : urls) {
			realm.addConstituent(url);
		}

		return realm.getClassLoader();
	}

	protected URL[] buildURLs(List<String> runtimeClasspathElements)
			throws MalformedURLException {
		// Add the projects classes and dependencies
		List<URL> urls = new ArrayList<URL>(runtimeClasspathElements.size());
		for (String element : runtimeClasspathElements) {
			final URL url = new File(element).toURI().toURL();
			urls.add(url);
			if (getLog().isDebugEnabled()) {
				getLog().debug("Added to project class loader: " + url);
			}
		}

		return urls.toArray(new URL[urls.size()]);
	}
}
