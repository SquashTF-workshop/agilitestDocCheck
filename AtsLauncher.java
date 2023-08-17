import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.module.ModuleDescriptor.Version;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AtsLauncher {

	/**
	 * This script is a Java class used as a simple script file with Java >= 14
	 * It will will try to update ATS tools from https://actiontesscript.com server and it will launch ATS suite tests using ATS components downloaded or already installed
	 * <p>
	 * Available options for launching ATS suites :
	 * <p>
	 * 'clean' : Clean all downloaded ATS components (libs + drivers) already installed on current system
	 * 'prepareMaven' : Prepare 'build.properties' file that maven can use to find ATS tools for ATS tests executions
	 * 'buildEnvironment' : Only try to get ATS tools path and create 'build.properties' file that can be used by Maven launch test process
	 * 'suiteXmlFiles' : Comma separated names of ATS suites xml files in 'exec' folder of current project, to be launched by this script
	 * 'atsReport' : Report details level
	 * 1 - Simple execution report
	 * 2 - Detailed execution report
	 * 3 - Detailed execution report with screen-shot
	 * 'validationReport' : Generate proof of functional execution with screen-shot
	 * 'atsListScripts' : List of ats scripts that can be launched using a temp suite execution
	 * 'tempSuiteName' : If 'atsListScripts' option is defined this option override default suite name ('tempSuite')
	 * 'disableSsl' : Disable trust certificat check when using ActionTestScript tools server
	 * 'atsToolsUrl' : Alternative url path to ActionTestScript tools server (the server have to send a list of ATS tools in a comma separated values data (name, version, folder_name, zip_archive_url).
	 * 'jenkinsUrl' : Url of a Jenkins server with saved ATS tools archives, tools will be available at [Jenkins_Url_Server]/userContent/tools using 'version.csv' files with names, versions and path of ATS tools
	 * 'reportsDirectory' (or 'output') : This is the output folder for all files generated during execution of ATS tests suites
	 * 'outbound' : By default, this script will try to contact ActionTestScript tools server.
	 * if this value is set to 'false', 'off' or '0', this script will try to find tools on local installation using following ordered methods :
	 * - 'atsToolsFolder' property in '.atsProjectProperties' file in current project folder
	 * - 'ATS_TOOLS' environment variable set on current machine
	 * - 'userprofile' folder directory : [userprofile]/.actiontestscript/tools
	 * <p>
	 * About '.atsProjectProperties' file in current project folder in xml format :
	 * - if tag 'atsToolsFolder' found : the value will define the local folder path of ATS tools
	 * - if tag 'atsToolsUrl' found : the standard ATS tools url server will be overwritten
	 * - if tag 'outbound' found : if the value is false, off or 0, no request will be send to get ATS tools
	 */

	//------------------------------------------------------------------------------------------------------------
	// Statics variables
	//------------------------------------------------------------------------------------------------------------

	private static final String ATS_SERVER = "https://actiontestscript.com";
	
	private static final String ATS_LAUNCHER_VERSION = "#ATS_LAUNCHER_VERSION#";
	private static final String ATS_VERSION = System.getenv("ATS_VERSION");
	private static final String OS = System.getProperty("os.name").toLowerCase();
	private static final String OS_TAG = "#OS#";

	private static final String ATS_RELEASES_SERVER = ATS_SERVER + "/releases";

	private static final String ATS_TOOLS_SERVER = ATS_SERVER + "/tools/" + OS_TAG + "/versions.php";
	private static final String ATS_JENKINS_TOOLS = "userContent/tools/versions.csv";

	private static final String TARGET = "target";
	private static final String SRC_EXEC = "src/exec";
	private static final String ATS_OUTPUT = "ats-output";
	private static final String BUILD_PROPERTIES = "build.properties";
	private static final String ATS_PROJECT_PROPERTIES = ".atsProjectProperties";

	private static final String LINUX = "linux";
	private static final String WINDOWS = "windows";
	private static final String MACOS = "macos";

	private static final String LINUX_DRIVER_NAME = "linuxdriver";
	private static final String MACOS_DRIVER_NAME = "macosdriver";

	private static final String TGZ = "tgz";
	private static final String ZIP = "zip";

	private static final String ATS = "ats";
	private static final String JDK = "jdk";
	private static final String JASPER = "jasper";

	private static final List<String> TRUE_LIST = Arrays.asList(new String[]{"on", "true", "1", "yes", "y"});
	private static final List<String> FALSE_LIST = Arrays.asList(new String[]{"off", "false", "0", "no", "n"});

	//------------------------------------------------------------------------------------------------------------
	// Execution variables
	//------------------------------------------------------------------------------------------------------------

	private static String operatingSystem = LINUX;
	private static String archiveExtension = TGZ;

	private static String suiteFiles = "";
	private static String atsScripts = "";
	private static String tempSuiteName = "tempSuite";
	private static String reportLevel = "";
	private static String validationReport = "0";
	private static String output = TARGET + "/" + ATS_OUTPUT;

	private static String atsToolsFolderProperty = "atsToolsFolder";
	private static String atsToolsUrlProperty = "atsToolsUrl";
	private static String outboundProperty = "outbound";
	private static String disableSSLParam = "disableSSL";

	private static String htmlReportParam = "1";

	private static String atsToolsFolder = null;
	private static String atsToolsUrl = null;

	private static String atsHomePath = null;
	private static String projectAtsVersion = null;
	private static String jdkHomePath = null;

	private static Map<String, String> atsExecEnv = new HashMap<String, String>();

	private static ArrayList<AtsToolEnvironment> atsToolsEnv = new ArrayList<AtsToolEnvironment>();

	private static String atsHomeInstall = System.getProperty("user.home");
	private static String atsToolsInstall = "/ats/tools";
	private static String atsCacheInstall = "/ats/cache";

	//------------------------------------------------------------------------------------------------------------
	// Main script execution
	//------------------------------------------------------------------------------------------------------------

	public static void main(String[] args) throws Exception, InterruptedException {

		printLog("Execute AtsLauncher script version -> " + ATS_LAUNCHER_VERSION);

		System.setProperty("https.protocols", "TLSv1.2,TLSv1.1,SSLv3");
		Set<PosixFilePermission> posixFilePermission = null;

		String toolsServer = ATS_TOOLS_SERVER;
		if(OS.contains("win")) {

			atsHomeInstall = System.getenv("APPDATA");
			toolsServer = toolsServer.replace(OS_TAG, "windows");

			operatingSystem = WINDOWS;
			archiveExtension = ZIP;

		}else {

			toolsServer = toolsServer.replace(OS_TAG, "linux");

			posixFilePermission = new HashSet<>();
			posixFilePermission.add(PosixFilePermission.OWNER_READ);
			posixFilePermission.add(PosixFilePermission.OWNER_WRITE);
			posixFilePermission.add(PosixFilePermission.OWNER_EXECUTE);

			posixFilePermission.add(PosixFilePermission.OTHERS_READ);
			posixFilePermission.add(PosixFilePermission.OTHERS_WRITE);
			posixFilePermission.add(PosixFilePermission.OTHERS_EXECUTE);

			posixFilePermission.add(PosixFilePermission.GROUP_READ);
			posixFilePermission.add(PosixFilePermission.GROUP_WRITE);
			posixFilePermission.add(PosixFilePermission.GROUP_EXECUTE);
		}

		atsToolsInstall = atsHomeInstall + atsToolsInstall;
		atsCacheInstall = atsHomeInstall + atsCacheInstall;

		printLog("Operating system detected -> " + operatingSystem);

		final Integer javaVersion = Runtime.version().version().get(0);

		if (javaVersion < 14) {
			printLog("Java version " + javaVersion + " found, minimum version 14 is needed to execute this script !");
			System.exit(0);
		}
		printLog("AtsLauncher execution using Java version -> " + javaVersion);

		final File script = new File(AtsLauncher.class.getProtectionDomain().getCodeSource().getLocation().getPath());

		Path projectFolderPath = Paths.get(script.getParent().replace("%20", " "));
		Path propFilePath = projectFolderPath.resolve(ATS_PROJECT_PROPERTIES).toAbsolutePath();

		if(!Files.exists(propFilePath)) {
			projectFolderPath = Path.of("").toAbsolutePath();
			propFilePath = projectFolderPath.resolve(ATS_PROJECT_PROPERTIES).toAbsolutePath();

			if(!Files.exists(propFilePath)) {
				printLog("Unable to find ATS project properties file, this script will stop now");
				System.exit(0);
			}
		}

		printLog("ATS project folder -> " + projectFolderPath.toString());

		final Path targetFolderPath = projectFolderPath.resolve(TARGET);

		String jenkinsToolsUrl = null;
		boolean buildEnvironment = false;
		boolean outboundTraffic = true;
		boolean disableSSLTrust = false;

		final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		//-------------------------------------------------------------------------------------------------
		// Read atsProjectProperties file
		//-------------------------------------------------------------------------------------------------

		try (InputStream is = new FileInputStream(propFilePath.toString())) {

			final Document doc = dbf.newDocumentBuilder().parse(is);

			if (doc.hasChildNodes()) {
				final Node root = doc.getChildNodes().item(0);
				if (root.hasChildNodes()) {
					final NodeList childs = root.getChildNodes();
					for (int i = 0; i < childs.getLength(); i++) {

						final String nodeName = childs.item(i).getNodeName();
						final String textContent = childs.item(i).getTextContent().trim();

						if (atsToolsFolderProperty.equalsIgnoreCase(nodeName)) {
							atsToolsFolder = textContent;
						} else if (atsToolsUrlProperty.equalsIgnoreCase(nodeName)) {
							atsToolsUrl = textContent;
						} else if (outboundProperty.equalsIgnoreCase(nodeName)) {
							outboundTraffic = FALSE_LIST.indexOf(textContent.toLowerCase()) == -1;
						} else if (disableSSLParam.equalsIgnoreCase(nodeName)) {
							disableSSLTrust = TRUE_LIST.indexOf(textContent.toLowerCase()) > -1;
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		//-------------------------------------------------------------------------------------------------
		// Read command line arguments
		//-------------------------------------------------------------------------------------------------

		boolean installOnly = false;

		for (int i = 0; i < args.length; i++) {

			final String allArgs = args[i].trim();
			final String firstArg = allArgs.toLowerCase();
			final int equalPos = firstArg.indexOf("=");

			if("clean".equals(firstArg)) {
				deleteDirectory(Paths.get(atsCacheInstall));
				deleteDirectory(Paths.get(atsToolsInstall));
			}

			if (equalPos == -1) {
				if ("buildenvironment".equals(firstArg) || "preparemaven".equals(firstArg)) {
					buildEnvironment = true;
					System.out.println("ok");
				} else if ("disablessl".equals(firstArg)) {
					disableSSLTrust = true;
				}else if ("install".equals(firstArg) || "installtools".equals(firstArg)) {
					installOnly = true;
				}
			} else {
				final String argName = firstArg.substring(0, equalPos).replaceAll("\\-", "");
				final String argValue = allArgs.substring(equalPos + 1).trim();

				switch (argName) {

				case "suitexmlfiles":
					suiteFiles = argValue;
					break;
				case "atslistscripts":
					atsScripts = argValue;
					break;
				case "tempsuitename":
					if (argValue.length() > 0) {
						tempSuiteName = argValue;
					}
					break;
				case "preparemaven":
					buildEnvironment = TRUE_LIST.indexOf(argValue.toLowerCase()) != -1;
					break;
				case "atsreport":
				case "reportlevel":
				case "report.level":
				case "ats.report":
				case "atsreportlevel":
					reportLevel = argValue;
					break;
				case "validationreport":
					validationReport = argValue;
					break;
				case "htmlplayer":
					htmlReportParam = argValue;
					break;
				case "reportsdirectory":
				case "output":
					output = argValue;
					break;
				case "atstoolsurl":
					atsToolsUrl = argValue;
					break;
				case "atstoolsfolder":
					atsToolsFolder = argValue;
					break;
				case "outbound":
					outboundTraffic = FALSE_LIST.indexOf(argValue.toLowerCase()) == -1;
					break;
				case "disablessl":
					disableSSLTrust = TRUE_LIST.indexOf(argValue.toLowerCase()) > -1;
					break;
				case "jenkinsurl":
					jenkinsToolsUrl = argValue;
					if (!jenkinsToolsUrl.endsWith("/")) {
						jenkinsToolsUrl += "/";
					}
					jenkinsToolsUrl += ATS_JENKINS_TOOLS;
					break;
				}
			}
		}

		if( ATS_VERSION == null || ATS_VERSION.isEmpty() || ATS_VERSION.trim().isEmpty()){

			//-------------------------------------------------------------------------------------------------
			// Read pom.xml file
			//-------------------------------------------------------------------------------------------------

			projectAtsVersion = getAtsVersion(dbf.newDocumentBuilder(), projectFolderPath.resolve("pom.xml").toAbsolutePath().toString());
			
			if(projectAtsVersion != null) {

				printLog("ATS library version defined in pom.xml -> " + projectAtsVersion);

				int install = 0;

				final Path currentAtsFolder = Paths.get(atsCacheInstall).resolve(projectAtsVersion);
				final Path currentLibsFolder = currentAtsFolder.resolve("libs");
				final Path currentDriversFolder = currentAtsFolder.resolve("drivers");

				if(Files.exists(currentLibsFolder)) {
					install = 1;
				}else {

					printLog("ATS releases server -> " + ATS_RELEASES_SERVER);

					try {
						final HttpURLConnection connection = (HttpURLConnection) new URI(ATS_RELEASES_SERVER + "/ats-libs/" + projectAtsVersion + ".zip").toURL().openConnection();
						if(connection.getResponseCode() == 200) {
							unzipArchive(
									download(
											connection, 
											"Download [Ats libs (" + projectAtsVersion + ")] -> "),
									currentLibsFolder);
							install = 1;

						}else {
							printLog("Server response error -> " + connection.getResponseCode());
						}
					}catch(Exception e) {
						printLog("Unable to connect to server -> " + ATS_RELEASES_SERVER);
					}
				}

				if(install > 0) {

					if(Files.exists(currentDriversFolder)) {
						install++;
					}else {

						Files.createDirectories(currentDriversFolder);

						final String versionUrl = getLastVersionUrl(ATS_RELEASES_SERVER + "/ats-drivers/" + operatingSystem + "/system");

						if(versionUrl != null) {
							try {
								final HttpURLConnection connection = (HttpURLConnection) new URI(versionUrl).toURL().openConnection();
								if(connection.getResponseCode() == 200) {

									final File downloaded =
											download(
													connection, 
													"Download [Ats system driver (" + systemDriverVersion + ")] -> ");

									if(WINDOWS.equals(operatingSystem)) {
										unzipArchive(downloaded, currentDriversFolder);
									}else if(LINUX.equals(operatingSystem)) {

										execute(
												new String[]{
														"tar",
														"-xzf",
														downloaded.getAbsolutePath(),
														"-C",
														currentDriversFolder.toFile().getAbsolutePath()});

										if(posixFilePermission != null) {
											final Path driverPath = currentDriversFolder.resolve(LINUX_DRIVER_NAME);
											Files.setPosixFilePermissions(driverPath, posixFilePermission);
										}

										downloaded.delete();
									}

									install++;
								}
							}catch(Exception e) {
								e.printStackTrace();
							}
						}
					}
				}

				final AtsToolEnvironment atsTool = new AtsToolEnvironment(ATS);
				if(install > 1) {
					atsTool.update(currentAtsFolder);
				}

				atsToolsEnv.add(atsTool);
				atsToolsEnv.add(new AtsToolEnvironment(JASPER));
				atsToolsEnv.add(new AtsToolEnvironment(JDK));

			}else {
				printLog("Unable to fin ATS library version defined in pom.xml !!");
			}

		} else {	

			projectAtsVersion = ATS_VERSION;

			printLog("ATS library version defined by environement variable -> " + projectAtsVersion);

			final AtsToolEnvironment atsTool = new AtsToolEnvironment(ATS);
			atsTool.update(Paths.get(atsCacheInstall).resolve(projectAtsVersion));

			atsToolsEnv.add(atsTool);
			atsToolsEnv.add(new AtsToolEnvironment(JASPER));
			atsToolsEnv.add(new AtsToolEnvironment(JDK));
		}

		//-------------------------------------------------------------------------------------------------
		// Check if SSL certificates trust is disabled
		//-------------------------------------------------------------------------------------------------

		if (disableSSLTrust) {
			disableSSL();
		}

		//-------------------------------------------------------------------------------------------------
		// Check and delete output directories
		//-------------------------------------------------------------------------------------------------

		Path atsOutput = Paths.get(output);
		if (!atsOutput.isAbsolute()) {
			atsOutput = projectFolderPath.resolve(output);
		}

		deleteDirectory(targetFolderPath);
		deleteDirectory(atsOutput);

		deleteDirectory(projectFolderPath.resolve("test-output"));

		//-------------------------------------------------------------------------------------------------
		// Check list ATS scripts
		//-------------------------------------------------------------------------------------------------

		String[] suiteFilesList = new String[0];

		if (atsScripts != null && atsScripts.trim().length() > 0) {

			Files.createDirectories(Paths.get(TARGET));
			suiteFiles = TARGET + "/" + tempSuiteName + ".xml";

			final StringBuilder builder = new StringBuilder("<!DOCTYPE suite SYSTEM \"https://testng.org/testng-1.0.dtd\">\n");
			builder.append("<suite name=\"").append(tempSuiteName).append("\" verbose=\"0\">\n<test name=\"testMain\" preserve-order=\"true\">\n<classes>\n");

			final Stream<String> atsScriptsList = Arrays.stream(atsScripts.split(","));
			atsScriptsList.forEach(a -> addScriptToSuiteFile(builder, a));

			builder.append("</classes>\n</test></suite>");

			try (PrintWriter out = new PrintWriter(suiteFiles)) {
				out.println(builder.toString());
				out.close();
			}

			suiteFilesList = new String[] {suiteFiles};

		}else if(suiteFiles != null) {
			String[] arr = suiteFiles.split(",");
			IntStream.range (0, arr.length).forEach (i -> {arr[i] = getSuitePath(arr[i]);});

			suiteFilesList = arr;
		}

		//-------------------------------------------------------------------------------------------------
		// if ATS server url has not been set using default url
		//-------------------------------------------------------------------------------------------------

		if (atsToolsUrl == null) {
			atsToolsUrl = toolsServer;
		}

		//-------------------------------------------------------------------------------------------------
		// try to get environment value
		//-------------------------------------------------------------------------------------------------

		if (atsToolsFolder == null) {
			atsToolsFolder = System.getenv("ATS_TOOLS");
		}

		if (reportLevel.isEmpty()) {
			String reportParam = System.getenv("ATS_REPORT");
			int tmp = 0;
			if (reportParam != null && !reportParam.isEmpty()) {
				try {
					tmp = Integer.parseInt(reportParam);
				}catch (NumberFormatException e){
					printLog("parameter can not be interpreted as number");
				}
			}
			if (tmp > 0 && tmp < 4) {
				reportLevel = Integer.toString(tmp);
			} else {
				reportLevel = "0";
			}
		}

		//-------------------------------------------------------------------------------------------------
		// if ats folder not defined using 'userprofile' home directory
		//-------------------------------------------------------------------------------------------------

		if (atsToolsFolder == null) {
			atsToolsFolder = atsToolsInstall;
		}

		//-------------------------------------------------------------------------------------------------

		final List<String> envList = new ArrayList<String>();

		boolean serverFound = false;
		String serverNotReachable = "ATS tools server is not reachable";

		if (outboundTraffic) {
			if (jenkinsToolsUrl != null) {
				serverFound = checkAtsToolsVersions(true, jenkinsToolsUrl);
			} else {
				serverFound = checkAtsToolsVersions(false, atsToolsUrl);
			}
		} else {
			serverNotReachable += " (outbound traffic has been turned off by user)";
		}

		if (!serverFound) {

			printLog(serverNotReachable);

			atsToolsEnv.stream().forEach(e -> installAtsTool(e, envList, Paths.get(atsToolsFolder)));

			if (atsToolsEnv.size() != envList.size()) {
				printLog("ATS tools not found in folder -> " + atsToolsFolder);
				System.exit(0);
			}

		} else {
			atsToolsEnv.stream().forEach(e -> installAtsTool(e, envList));
		}

		if(installOnly) {
			System.out.println("====================================================");
			printLog("ATS tools and components installed !");
			System.out.println("====================================================");
			System.exit(0);
		}

		if (buildEnvironment) {

			final Path p = projectFolderPath.resolve(BUILD_PROPERTIES);
			Files.deleteIfExists(p);

			Files.write(p, String.join("\n", envList).getBytes(), StandardOpenOption.CREATE);

			printLog("Build properties file created : " + p.toFile().getAbsolutePath());

		} else {

			final File projectDirectoryFile = projectFolderPath.toFile();
			final Path generatedPath = targetFolderPath.resolve("generated");
			final File generatedSourceDir = generatedPath.toFile();
			final String generatedSourceDirPath = generatedSourceDir.getAbsolutePath();

			generatedSourceDir.mkdirs();

			printLog("Project directory -> " + projectDirectoryFile.getAbsolutePath());
			printLog("Generate java files -> " + generatedSourceDirPath);

			final FullLogConsumer logConsumer = new FullLogConsumer();

			final String javaRunCommand = new StringBuilder(Paths.get(jdkHomePath).toAbsolutePath().toString()).append("/bin/java").toString();

			String[] command = 
					new String[]{
							javaRunCommand,
							"-cp",
							atsHomePath + "/libs/*",
							"com.ats.generator.Generator",
							"-prj",
							projectFolderPath.toString(),
							"-dest",
							targetFolderPath.toString() + "/generated",
							"-force"
			};

			execute(command,
					null,
					projectDirectoryFile,
					logConsumer,
					logConsumer);

			final ArrayList<String> files = listJavaClasses(generatedSourceDirPath.length() + 1, generatedSourceDir);

			final Path classFolder = targetFolderPath.resolve("classes").toAbsolutePath();
			final Path classFolderAssets = classFolder.resolve("assets");
			classFolderAssets.toFile().mkdirs();

			copyFolder(projectFolderPath.resolve("src").resolve("assets"), classFolderAssets);

			//----------------------------------------------------------------------------------------

			printLog("Compile classes to folder -> " + classFolder.toString());
			Files.write(generatedPath.resolve("JavaClasses.list"), String.join("\n", files).getBytes(), StandardOpenOption.CREATE);

			command = new String[]{
					javaRunCommand + "c",
					"-cp",
					"../../libs/*" + File.pathSeparator + atsHomePath + "/libs/*",
					"-d",
					classFolder.toString(),
					"@JavaClasses.list"
			};

			execute(command,
					null,
					generatedPath.toAbsolutePath().toFile(),
					logConsumer,
					logConsumer);

			//----------------------------------------------------------------------------------------

			printLog("Launch suite(s) execution -> " + suiteFiles);

			command = new String[]{
					javaRunCommand,
					"-Dats-report=" + reportLevel,
					"-Dvalidation-report=" + validationReport,
					"-Dhtmlplayer=" + htmlReportParam,
					"-Doutbound-traffic=" + outboundTraffic,
					"-cp",
					atsHomePath + "/libs/*" + File.pathSeparator + targetFolderPath.toString() + "/classes" + File.pathSeparator + "libs/*",
					"org.testng.TestNG",
					"-d",
					atsOutput.toString()
			};

			command = concatWithArrayCopy(command, suiteFilesList);

			execute(command,
					atsExecEnv,
					projectDirectoryFile,
					logConsumer,
					new TestNGLogConsumer());
		}
	}

	//------------------------------------------------------------------------------------------------------------
	// Functions
	//------------------------------------------------------------------------------------------------------------

	private static <T> T[] concatWithArrayCopy(T[] array1, T[] array2) {
		T[] result = Arrays.copyOf(array1, array1.length + array2.length);
		System.arraycopy(array2, 0, result, array1.length, array2.length);
		return result;
	}

	private static String getSuitePath(String name) {

		name = name.replaceAll("\"", "");

		if(!name.startsWith(SRC_EXEC)) {
			name = SRC_EXEC + "/" + name;
		}

		if(!name.endsWith(".xml")) {
			name += ".xml";
		}

		return name;
	}

	private static final Pattern SYS_VERSION_PATTERN = Pattern.compile("<a href\\s?=\\s?\"([^\"]+\\.(zip|tgz))\">");
	private static String systemDriverVersion = "";

	private static String getLastVersionUrl(String folderUrl) throws MalformedURLException, IOException, URISyntaxException {

		final HttpURLConnection con = (HttpURLConnection) new URI(folderUrl).toURL().openConnection();
		if(con.getResponseCode() == 200) {

			final InputStream inputStream = con.getInputStream();
			final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

			final StringBuilder builder = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null) {
				builder.append(line);
			}
			inputStream.close();

			final ArrayList<String> versions = new ArrayList<String>();

			final Matcher matcher = SYS_VERSION_PATTERN.matcher(builder.toString());
			int index = 0;
			while (matcher.find(index)) {
				versions.add(matcher.group(1));
				index = matcher.end();
			}

			if(versions.size() > 0) {
				final Version version = versions.stream()
						.map(Version::parse)
						.sorted(Collections.reverseOrder())
						.findFirst().get();

				if(version != null) {
					systemDriverVersion = version.toString();
					return folderUrl + "/" + systemDriverVersion;
				}
			}
		}

		return null;
	}

	private static void addScriptToSuiteFile(StringBuilder builder, String scriptName) {
		scriptName = scriptName.replaceAll("\\/", ".");
		if (scriptName.endsWith(".ats")) {
			scriptName = scriptName.substring(0, scriptName.length() - 4);
		}

		if (scriptName.startsWith(".")) {
			scriptName = scriptName.substring(1);
		}

		builder.append("<class name=\"").append(scriptName).append("\"/>\n");
	}

	private static Map<String, String[]> getServerToolsVersion(String serverUrl) {

		final Map<String, String[]> versions = new HashMap<String, String[]>();
		try {
			final URL url = new URI(serverUrl).toURL();

			HttpURLConnection.setFollowRedirects(false);
			final HttpURLConnection yc = (HttpURLConnection)url.openConnection();

			yc.setRequestMethod("GET");
			yc.setRequestProperty("Connection", "Keep-Alive");
			yc.setRequestProperty("Cache-Control", "no-cache");
			yc.setRequestProperty("User-Agent", "AtsLauncher-" + operatingSystem);

			yc.setUseCaches(false);
			yc.setDoOutput(true);

			final BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				String[] lineData = inputLine.split(",");
				versions.put(lineData[0], lineData);
			}

			in.close();

		} catch (IOException | URISyntaxException e) {
			printLog("AtsLauncher error -> " + e.getMessage());
		}

		return versions;
	}

	private static Boolean checkAtsToolsVersions(boolean localServer, String server) {

		final Map<String, String[]> versions = getServerToolsVersion(server);

		if (versions.size() < atsToolsEnv.size() && localServer) {
			printLog("Unable to get all ATS tools on this server -> " + server);
			versions.putAll(getServerToolsVersion(atsToolsUrl));
			if (versions.size() < atsToolsEnv.size()) {
				return false;
			}
		}

		for (AtsToolEnvironment t : atsToolsEnv) {
			if(t.check) {
				final String[] toolData = versions.get(t.name);
				if (toolData != null) {
					final String folderName = toolData[2];
					t.folderName = folderName;

					final File toolFolder = Paths.get(atsToolsFolder).resolve(folderName).toFile();
					if (toolFolder.exists()) {
						t.folder = toolFolder.getAbsolutePath();
					} else {
						t.url = toolData[3];
					}
				} else {
					return false;
				}
			}
		}

		return true;
	}

	private static void toolInstalled(AtsToolEnvironment tool, List<String> envList) {

		envList.add(tool.envName + "=" + tool.folder);
		printLog("Set environment variable [" + tool.envName + "] -> " + tool.folder);

		atsExecEnv.put(tool.envName, tool.folder);

		if (ATS.equals(tool.name)) {
			atsHomePath = tool.folder;
		} else if (JDK.equals(tool.name)) {
			jdkHomePath = tool.folder;
		}
	}

	private static void installAtsTool(AtsToolEnvironment tool, List<String> envList, Path toolsPath) {
		if(!tool.check) {
			toolInstalled(tool, envList);
		}else {	
			try (Stream<Path> stream = Files.walk(toolsPath, 1)) {
				final List<String> folders = stream
						.filter(file -> file != toolsPath && Files.isDirectory(file) && file.getFileName().toString().startsWith(tool.name))
						.map(Path::getFileName)
						.map(Path::toString)
						.collect(Collectors.toList());

				if (folders.size() > 0) {
					folders.sort(Collections.reverseOrder());

					tool.folder = toolsPath.resolve(folders.get(0)).toAbsolutePath().toString();
					toolInstalled(tool, envList);
				}

			} catch (Exception e) {}
		}
	}

	private static void installAtsTool(AtsToolEnvironment tool, List<String> envList) {
		if(!tool.check) {
			toolInstalled(tool, envList);
		}else {	
			if (tool.folderName == null) {

				final File[] files = Paths.get(atsToolsFolder).toFile().listFiles();
				Arrays.sort(files, Comparator.comparingLong(File::lastModified));

				for (File f : files) {
					if (f.getName().startsWith(tool.name)) {
						tool.folderName = f.getName();
						tool.folder = f.getAbsolutePath();
						break;
					}
				}

			} else if(tool.url != null){

				printLog("Download ATS tool -> " + tool.url);

				try {
					final File tmpZipFile = Files.createTempDirectory("atsTool_").resolve(tool.folderName + "." + archiveExtension).toAbsolutePath().toFile();

					if (tmpZipFile.exists()) {
						tmpZipFile.delete();
					}

					final HttpURLConnection con = (HttpURLConnection) new URI(tool.url).toURL().openConnection();

					IntConsumer consume;
					final int fileLength = con.getContentLength();
					if (fileLength == -1) {
						consume = (p) -> {
							System.out.println("Download [" + tool.name + "] -> " + p + " Mo");
						};
					} else {
						consume = (p) -> {
							System.out.println("Download [" + tool.name + "] -> " + p + " %");
						};
					}

					ReadableConsumerByteChannel rcbc = new ReadableConsumerByteChannel(
							Channels.newChannel(con.getInputStream()),
							fileLength,
							consume);

					final FileOutputStream fosx = new FileOutputStream(tmpZipFile);
					fosx.getChannel().transferFrom(rcbc, 0, Long.MAX_VALUE);
					fosx.close();

					if(LINUX.equals(operatingSystem)) {

						final Path path = Paths.get(atsToolsFolder);
						Files.createDirectories(path);

						try {
							execute(
									new String[]{
											"tar",
											"-xzf",
											tmpZipFile.getAbsolutePath(),
											"-C",
											path.toFile().getAbsolutePath()});

						}catch(Exception e) {}

					}else {
						unzipArchive(tmpZipFile, Paths.get(atsToolsFolder));
					}

				} catch (IOException | URISyntaxException e) {
					e.printStackTrace();
				}

				tool.folder = Paths.get(atsToolsFolder).resolve(tool.folderName).toFile().getAbsolutePath();
			}

			if (tool.folder == null) {
				throw new RuntimeException("ATS tool is not installed on this system -> " + tool.name);
			} else {
				toolInstalled(tool, envList);
			}
		}
	}	

	private static String getAtsVersion(DocumentBuilder db, String pomFilePath) {
		
		try (InputStream is = new FileInputStream(pomFilePath)) {

			final Document doc = db.parse(is);

			final NodeList project = doc.getElementsByTagName("project");
			if (project.getLength() > 0) {
				final NodeList projectItems = project.item(0).getChildNodes();
				for (int i=0; i < projectItems.getLength(); i++) {
					if("dependencies".equals(projectItems.item(i).getNodeName())) {

						final NodeList dependencies = projectItems.item(i).getChildNodes();
						for (int j=0; j < dependencies.getLength(); j++) {

							String artifactId = null;
							String groupId = null;
							String version = null;

							final NodeList dependency = dependencies.item(j).getChildNodes();
							for (int k=0; k < dependency.getLength(); k++) {
								if("artifactId".equals(dependency.item(k).getNodeName())) {
									artifactId = dependency.item(k).getTextContent();
								}else if("groupId".equals(dependency.item(k).getNodeName())) {
									groupId = dependency.item(k).getTextContent();
								}else if("version".equals(dependency.item(k).getNodeName())) {
									version = dependency.item(k).getTextContent();
								}
							}

							if("com.actiontestscript".equals(groupId) && "ats-automated-testing".equals(artifactId)) {
								if(!"${ats.lib.version}".equals(version)) {
									return version;
								}
							}
						}
					}
				}
				
				for (int i=0; i < projectItems.getLength(); i++) {
					if("properties".equals(projectItems.item(i).getNodeName())) {
						final NodeList properties = projectItems.item(i).getChildNodes();
						for (int j=0; j < properties.getLength(); j++) {
							final Node property = properties.item(j);
							if("ats.lib.version".equals(property.getNodeName())) {
								return property.getTextContent();
							}
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private static File download(HttpURLConnection connection, String logString) throws Exception {

		Path folder = Files.createTempDirectory("ats_download");
		final File tmpFile = folder.resolve("tmp").toAbsolutePath().toFile();

		if (tmpFile.exists()) {
			tmpFile.delete();
		}

		final int fileLength = connection.getContentLength();

		IntConsumer consume;
		if (fileLength == -1) {
			consume = (p) -> {
				System.out.println(logString + p + " Mo");
			};
		} else {
			consume = (p) -> {
				System.out.println(logString + p + " %");
			};
		}

		ReadableConsumerByteChannel rcbc = new ReadableConsumerByteChannel(
				Channels.newChannel(connection.getInputStream()),
				fileLength,
				consume);

		final FileOutputStream fosx = new FileOutputStream(tmpFile);
		fosx.getChannel().transferFrom(rcbc, 0, Long.MAX_VALUE);
		fosx.close();

		return tmpFile;
	}

	//------------------------------------------------------------------------------------------------------------
	// Classes
	//------------------------------------------------------------------------------------------------------------

	private static class FullLogConsumer implements Consumer<String> {
		@Override
		public void accept(String s) {
			System.out.println(s);
		}
	}

	private static class TestNGLogConsumer implements Consumer<String> {
		@Override
		public void accept(String s) {
			System.out.println(
					s.replace("[TestNG]", "")
					.replace("[main] INFO org.testng.internal.Utils -", "[TestNG]")
					.replace("Warning: [org.testng.ITest]", "[TestNG] Warning :")
					.replace("[main] INFO org.testng.TestClass", "[TestNG]")
					);
		}
	}

	private static class StreamGobbler extends Thread {
		private InputStream inputStream;
		private Consumer<String> consumer;

		public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
			this.inputStream = inputStream;
			this.consumer = consumer;
		}

		@Override
		public void run() {
			new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
		}
	}

	private static class AtsToolEnvironment {

		public String name;
		public String envName;
		public String folder;
		public String folderName;
		public boolean check = true;

		public String url;

		public AtsToolEnvironment(String name) {
			this.name = name;
			this.envName = name.toUpperCase() + "_HOME";
		}

		public void update(Path folder) {
			this.folder = folder.toAbsolutePath().toString();
			this.folderName = folder.getFileName().toString();
			this.check = false;
		}
	}

	private static class ReadableConsumerByteChannel implements ReadableByteChannel {

		private final ReadableByteChannel rbc;
		private final IntConsumer onRead;
		private final int totalBytes;

		private int totalByteRead;

		private int currentPercent = 0;

		public ReadableConsumerByteChannel(ReadableByteChannel rbc, int totalBytes, IntConsumer onBytesRead) {
			this.rbc = rbc;
			this.totalBytes = totalBytes;
			this.onRead = onBytesRead;
		}

		@Override
		public int read(ByteBuffer dst) throws IOException {
			int nRead = rbc.read(dst);
			notifyBytesRead(nRead);
			return nRead;
		}

		protected void notifyBytesRead(int nRead) {
			if (nRead <= 0) {
				return;
			}
			totalByteRead += nRead;

			if (totalBytes != -1) {
				int percent = (int) (((float) totalByteRead / totalBytes) * 100);
				if (percent % 5 == 0 && currentPercent != percent) {
					currentPercent = percent;
					onRead.accept(currentPercent);
				}
			} else if (totalByteRead % 10000 == 0) {
				onRead.accept(totalByteRead / 10000);
			}
		}

		@Override
		public boolean isOpen() {
			return rbc.isOpen();
		}

		@Override
		public void close() throws IOException {
			rbc.close();
		}
	}

	//------------------------------------------------------------------------------------------------------------
	// Utils
	//------------------------------------------------------------------------------------------------------------

	private static void printLog(String data) {
		System.out.println("[ATS-LAUNCHER] " + data);
	}

	private static void execute(String[] commands, Map<String, String> execEnv, File currentDir, Consumer<String> outputConsumer, Consumer<String> errorConsumer) throws IOException, InterruptedException {

		final ProcessBuilder pb = new ProcessBuilder(commands).directory(currentDir);
		if(execEnv == null) {
			execEnv = System.getenv();
		}else {
			execEnv.putAll(System.getenv());
		}

		pb.environment().putAll(execEnv);

		final Process p = pb.start();

		new StreamGobbler(p.getErrorStream(), errorConsumer).start();
		new StreamGobbler(p.getInputStream(), outputConsumer).start();

		p.waitFor();
	}

	private static void execute(String[] commands) throws IOException, InterruptedException {

		System.out.println("---------exec-----------------------");
		final ProcessBuilder pb = new ProcessBuilder(commands);
		final Process p = pb.start();

		p.waitFor();

		System.out.println("-----------------------------exec----");
	}

	//------------------------------------------------------------------------------------------------------------
	// Files
	//------------------------------------------------------------------------------------------------------------

	private static void unzipArchive(File archive, Path destination) {
		try {
			unzipFolder(archive.toPath(), destination);
		} catch (IOException e) {
			e.printStackTrace();
		}
		archive.delete();
	}

	private static void unzipFolder(Path source, Path target) throws IOException {

		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(source.toFile()))) {

			ZipEntry zipEntry = zis.getNextEntry();

			while (zipEntry != null) {

				boolean isDirectory = false;

				if (zipEntry.getName().endsWith(File.separator) || zipEntry.isDirectory()) {
					isDirectory = true;
				}

				Path newPath = zipSlipProtect(zipEntry, target);

				if (isDirectory) {
					Files.createDirectories(newPath);
				} else {

					if (newPath.getParent() != null) {
						if (Files.notExists(newPath.getParent())) {
							Files.createDirectories(newPath.getParent());
						}
					}
					Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
				}
				zipEntry = zis.getNextEntry();
			}
			zis.closeEntry();
		}
	}

	private static Path zipSlipProtect(ZipEntry zipEntry, Path targetDir) throws IOException {
		Path targetDirResolved = targetDir.resolve(zipEntry.getName());
		Path normalizePath = targetDirResolved.normalize();
		if (!normalizePath.startsWith(targetDir)) {
			throw new IOException("Bad zip entry: " + zipEntry.getName());
		}
		return normalizePath;
	}
	private static void copyFolder(Path src, Path dest) throws IOException {
		try (Stream<Path> stream = Files.walk(src)) {
			stream.forEach(source -> copy(source, dest.resolve(src.relativize(source))));
		}
	}

	private static void copy(Path source, Path dest) {
		try {
			Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	private static ArrayList<String> listJavaClasses(int subLen, File directory) {

		final ArrayList<String> list = new ArrayList<String>();
		final File[] fList = directory.listFiles();

		if (fList == null) {
			throw new RuntimeException("Directory list files return null value ! (" + directory.getAbsolutePath() + ")");
		} else {
			for (File file : fList) {
				if (file.isFile()) {
					if (file.getName().endsWith(".java")) {
						list.add(file.getAbsolutePath().substring(subLen).replaceAll("\\\\", "/"));
					}
				} else if (file.isDirectory()) {
					list.addAll(listJavaClasses(subLen, file));
				}
			}
		}

		return list;
	}

	private static void deleteDirectory(Path directory) throws IOException {
		if (Files.exists(directory)) {
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
					Files.delete(path);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path directory, IOException ioException) throws IOException {
					Files.delete(directory);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}

	//------------------------------------------------------------------------------------------------------------
	// Tools
	//------------------------------------------------------------------------------------------------------------

	public static void disableSSL() throws NoSuchAlgorithmException, KeyManagementException {

		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {

			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		}};

		// Install the all-trusting trust manager

		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, trustAllCerts, new java.security.SecureRandom());

		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

		// Create all-trusting host name verifier

		HostnameVerifier allHostsValid = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		};

		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
	}
}
