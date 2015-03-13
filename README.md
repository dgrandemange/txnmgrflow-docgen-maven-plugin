# txnmgrflow-docgen-maven-plugin

A Maven plugin to generate HTML documentation from transaction manager configuration file(s).
Generated documentation consists of a (set of) SVG converted DOT directed graphs representing the transaction manager flow.

Requirements :
--------------
You'll need GraphViz V2.28+ installed on your desktop.
Documentation navigation requires a recent FireFox browser.

Steps to add this plugin in your Maven project :
------------------------------------------------
You shall add the following snippets to your pom.xml.

First, declare the Maven repository where this plugin releases are available :

    <repositories>
    
      <!-- ... your specific repositories if there are any ... -->
    
  		<repository>
  			<id>dgrandemange-mvn-repo-releases</id>
  			<name>dgrandemange GitHub Maven Repository releases</name>
  			<url>https://raw.githubusercontent.com/dgrandemange/dgrandemange-mvn-repo/master/releases/</url>
  		</repository>
    
    </repositories>

Now, complete the build plugins section with a new plugin declaration. It should eventually look like this :

	<build>
		<plugins>
		  
		  <!-- ... -->
		  
			<plugin>
				<groupId>fr.dgrandemange</groupId>
				<artifactId>txnmgrflow-docgen-maven-plugin</artifactId>
				<version>x.y.z</version>
				<configuration>
					<!-- 
						"graphVizDotCmdPath" [REQUIRED] : full path to the GrapViz dot command
					-->
					<graphVizDotCmdPath>C:\Program Files (x86)\Graphviz2.38\bin\dot.exe</graphVizDotCmdPath>
				</configuration>

				<executions>
				
					<!-- Note : declare one execution section per tx manager config doc to generate -->
					<execution>
						<configuration>
							<!-- 
								"txnmgrConfigPath" [REQUIRED] : tx manager config file location 
								NB : use Maven property ${basedir} to avoid hard coded paths 
							-->
							<txnmgrConfigPath>${basedir}\src\main\resources\fr\dgrandemange\springframework\ext\txnmgr\xml\application-context.xml</txnmgrConfigPath>
							
							<!-- 
								"docGenDirName" [OPTIONAL] : name of the directory under "${basedir}/target/txnmgrDocGen/" where the doc should be generated; 
								NB : default value is set to tx mgr config file name
							-->
							<docGenDirName>financialFlow</docGenDirName>
							
							<!-- 
								"alias" [OPTIONAL] : a user friendly title for documentation main page
								NB : default value is set to current project name
							-->
							<alias>Financial workflow sample</alias>
							
							<!-- 
								"subflowMode" [OPTIONAL] : whether to generate one doc page per subflow (when available in the tx mgr config) or not
								NB : default value is "true"
							-->
							<subflowMode>false</subflowMode>
						</configuration>

						<goals>
							<goal>docgen</goal>
						</goals>

						<phase>prepare-package</phase>
					</execution>
					
				</executions>
			</plugin>
			
		</plugins>
	</build>
	
Usage :
-------
Run maven install on your project. Documentation should now be generated under "target/txnmgrDocGen/".
Open index.html with a recent FireFox browser, and enjoy graphs navigation !
