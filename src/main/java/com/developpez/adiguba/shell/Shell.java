/*
 * Shell for Java © adiGuba (http://adiguba.developpez.com)
 * adiGuba (mars 2007)
 *
 * Contact  : adiguba@redaction-developpez.com
 * Site web : http://adiguba.developpez.com/
 *
 * Ce logiciel est une librairie Java servant à simplifier l'exécution
 * de programme natif ou de ligne de commande du shell depuis une
 * application Java, sans se soucier du système hôte. 
 * 
 * Ce logiciel est régi par la licence CeCILL-C soumise au droit français et
 * respectant les principes de diffusion des logiciels libres. Vous pouvez
 * utiliser, modifier et/ou redistribuer ce programme sous les conditions
 * de la licence CeCILL-C telle que diffusée par le CEA, le CNRS et l'INRIA 
 * sur le site "http://www.cecill.info".
 * 
 * En contrepartie de l'accessibilité au code source et des droits de copie,
 * de modification et de redistribution accordés par cette licence, il n'est
 * offert aux utilisateurs qu'une garantie limitée.  Pour les mêmes raisons,
 * seule une responsabilité restreinte pèse sur l'auteur du programme,  le
 * titulaire des droits patrimoniaux et les concédants successifs.
 * 
 * A cet égard  l'attention de l'utilisateur est attirée sur les risques
 * associés au chargement,  à l'utilisation,  à la modification et/ou au
 * développement et à la reproduction du logiciel par l'utilisateur étant 
 * donné sa spécificité de logiciel libre, qui peut le rendre complexe à 
 * manipuler et qui le réserve donc à des développeurs et des professionnels
 * avertis possédant  des  connaissances  informatiques approfondies.  Les
 * utilisateurs sont donc invités à charger  et  tester  l'adéquation  du
 * logiciel à leurs besoins dans des conditions permettant d'assurer la
 * sécurité de leurs systèmes et ou de leurs données et, plus généralement, 
 * à l'utiliser et l'exploiter dans les mêmes conditions de sécurité. 
 * 
 * Le fait que vous puissiez accéder à cet en-tête signifie que vous avez 
 * pris connaissance de la licence CeCILL-C, et que vous en avez accepté les
 * termes.
 * 
 */
package com.developpez.adiguba.shell;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Cette classe représente le shell système.<br>
 * Elle permet de simplifier l'exécution de programme externe.<br>
 * <br>
 * Cette classe utilise la classe {@linkplain ProcessConsumer} afin de gérer
 * simplement les flux d'entrées/sorties du process.
 * 
 * @author adiGuba
 * @version shell-1.0
 */
public class Shell {

	/** Commande permettant de lancer le shell sous les systèmes Windows 9x. */
	private static final String[] DEFAULT_WIN9X_SHELL = {"command.com", "/C"};
	/** Commande permettant de lancer le shell sous les systèmes Windows NT/XP/Vista. */
	private static final String[] DEFAULT_WINNT_SHELL = {"cmd.exe", "/C"};
	/** Commande permettant de lancer le shell sous les systèmes Unix/Linux/MacOS/BSD. */
	private static final String[] DEFAULT_UNIX_SHELL = {"/bin/sh", "-c"};
	
	/** Shell du système courant, déterminé lors du chargement de la classe. */
	private static final String[] SYSTEM_SHELL = getSystemShell();
	
	/**
	 * Retourne le shell courant sous forme d'un tableau de String
	 * représentant les différents paramètres a exécuter.<br/>
	 * Le shell à utiliser dépend du système d'exploitation et
	 * de certaine variable d'environnement (<b>%ComSpec%</b> sous Windows,
	 * <b>$SHELL</b> sous les autres systèmes).
	 * @return Le tableau de paramètre utile à l'exécution du shell.
	 */
	private static String[] getSystemShell() {
		// On détermine le shell selon deux cas : Windows ou autres :
		String osName = System.getProperty("os.name");
		if (osName.startsWith("Windows")) {
			// On tente de déterminer le shell selon la variable d'environnement ComSpec :
			String comspec = System.getenv("ComSpec");
			if (comspec!=null) {
				return new String[] {comspec, "/C"};
			}
			// Sinon on détermine le shell selon le nom du système :
			if (osName.startsWith("Windows 3") || osName.startsWith("Windows 95")
				|| osName.startsWith("Windows 98") || osName.startsWith("Windows ME")) {
				return DEFAULT_WIN9X_SHELL;
			}
			return DEFAULT_WINNT_SHELL;
		}
		// On tente de déterminer le shell selon la variable d'environnement SHELL :
		String shell = System.getenv("SHELL");
		if (shell!=null) {
			return new String[] {shell, "-c"};
		}
		// Sinon on utilise le shell par défaut (/bin/sh)
		return DEFAULT_UNIX_SHELL;
	}
	
	/** Le tableau représentant les paramètres du shell. */
	private final String[] shell;
	/** Le charset associé à cette instance du shell. */
	private Charset charset = null;
	/** Le répertoire associé à cette instance du shell. */
	private File directory = null;
	/** Les variables d'environnement utilisateurs associé à ce shell. */
	private Map<String,String> userEnv = null;
	/** Indique si les variables d'environnements globales doivent être hérité. */
	private boolean systemEnvInherited = true;	
	
	/**
	 * Construit un nouveau Shell en utilisant le shell systeme.
	 */
	public Shell() {
		this.shell = Shell.SYSTEM_SHELL;
	}
	
	/**
	 * Construit un nouveau shell en utilisant la commande représenté en paramètre.
	 * Par exemple pour forcer l'utilisation du bash :
	 * <pre><code>Shell sh = new Shell("/bin/bash", "-c");</code></pre>
	 * @param cmds Les paramètres permettant de lancer le shell.
	 */
	public Shell(String...cmds) {
		this.shell = new String[cmds.length];
		System.arraycopy(cmds, 0, this.shell, 0, this.shell.length);
	}
	
	/**
	 * Retourne le charset associé avec cette instance de shell.
	 * @return Charset.
	 */
	public Charset getCharset() {
		if (this.charset==null) {
			this.charset = Charset.defaultCharset();
		}
		return this.charset;
	}
	
	/**
	 * Modifie le charset associé avec cette instance de shell.
	 * @param charset Le nouveau charset a utiliser.
	 */
	public void setCharset(Charset charset) {
		this.charset = charset;
	}


	/**
	 * Modifie le charset associé avec cette instance de shell.
	 * @param charsetName Le nom du nouveau charset
	 * @throws IllegalCharsetNameException
	 * @throws UnsupportedCharsetException
	 */
	public void setCharset(String charsetName)
		throws IllegalCharsetNameException, UnsupportedCharsetException {
		this.charset = Charset.forName(charsetName);
	}


	/**
	 * Retourne une map contenant les variables d'environnements utilisateurs.
	 * Cette Map est librement modifiables afin d'ajouter/supprimer des éléments.
	 * @return Map des variables d'environnements utilisateurs.
	 */
	public Map<String, String> getUserEnv() {
		if (this.userEnv==null) {
			this.userEnv = new HashMap<String, String>();
		}
		return this.userEnv;
	}


	/**
	 * Retourne le répertoire à partir duquel les commandes du shell seront lancés.
	 * @return Le répertoire courant.
	 */
	public File getDirectory() {
		if (this.directory==null) {
			this.directory = new File("").getAbsoluteFile();
		}
		return this.directory;
	}

	/**
	 * Modifie le répertoire à partir duquel les commandes du shell seront lancés.
	 * @param directory Le nouveau répertoire
	 * @throws IllegalArgumentException Si <code>directory</code> ne représente pas un répertoire.
	 */
	public void setDirectory(File directory) {
		if (!directory.isDirectory()) {
			throw new IllegalArgumentException("Not a directory");
		}
		this.directory = directory;
	}

	/**
	 * Indique si les variables d'environnements de l'application Java
	 * courante doivent être passé aux commandes lancées par ce shell.
	 * @return <b>true</b> si les nouveaux process héritent des variables d'environnements,
	 * <b>false</b> sinon.
	 */
	public boolean isSystemEnvInherited() {
		return this.systemEnvInherited;
	}

	/**
	 * Modifie la valeur de l'attribut 'inheritSystemEnv'.
	 * @param inheritSystemEnv La nouvelle valeur de l'attribut.
	 * @see Shell#isSystemEnvInherited()
	 */
	public void setSystemEnvInherited(boolean inheritSystemEnv) {
		this.systemEnvInherited = inheritSystemEnv;
	}
	
	
	/**
	 * Creer un ProcessBuilder selon la configuration de ce shell
	 * @param args Les paramètres principaux de la commande
	 * @return Un ProcessBuilder correctement initialisé.
	 */
	private ProcessBuilder create(String...args) {
		ProcessBuilder pb = new ProcessBuilder(args);
		pb.directory(directory);
		if (!systemEnvInherited) {
			pb.environment().clear();
		}
		if (userEnv!=null) {
			pb.environment().putAll(userEnv);
		}
		return pb;
	}
	
	/**
	 * Crée un processus représentant le shell et l'associe à une instance
	 * de ProcessConsumer.<br/>
	 * Le processus ne sera réellement démarré que lors de l'appel d'une
	 * des méthodes <code>consume()</code> de la classe ProcessConsumer.<br>
	 * <br>
	 * Cela permet de lancer plusieurs commandes dans le même shell
	 * via le flux d'entrée du processus.
	 * @return Une instance de ProcessConsumer associé au processus du shell.
	 * @see ProcessConsumer#consume()
	 * @see ProcessConsumer#input(Readable)
	 */
	public ProcessConsumer shell() {
        return new ProcessConsumer(create(this.shell[0]), this.charset);
    }
	
	/**
	 * Crée un processus représentant la commande du shell et l'associe
	 * à une instance de ProcessConsumer.<br/>
	 * Cette méthode instancie un nouveau shell en tant que processus afin
	 * d'exécuter la ligne de commande en paramètre. Le processus ne sera
	 * réellement démarré que lors de l'appel d'une des méthodes
	 * <code>consume()</code> de la classe ProcessConsumer.<br>
	 * <br>
	 * La commande passé en paramètre accepte toutes les spécificitées du
	 * shell système (redirections, pipes, structures conditionnelles, etc.).
	 * @param commandLine La ligne de commande à exécuter par le shell .
	 * @return Une instance de ProcessConsumer associé à la commande.
	 * @see ProcessConsumer#consume()
	 */
    public ProcessConsumer command(String commandLine) {
    	ProcessBuilder pb = create(this.shell);
    	pb.command().add(commandLine);
    	return new ProcessConsumer(pb ,this.charset);
    }
    
    /**
     * Identique à {@link Shell#command(String)}, si ce n'est que la ligne
     * de commande est d'abord formatté en utilisant les paramètres via
	 * la classe MessageFormat.<br><br>
	 * Cete méthode est équivalent à la ligne suivante :<br>
	 * <pre><code>command(MessageFormat.format(commandLine, arguments))</code></pre>
     * @param commandLine La ligne de commande à formater.
     * @param arguments Les paramètres du formattage.
     * @return Une instance de ProcessConsumer associé à la commande.
     * @see MessageFormat#format(String, Object...)
     * @see Shell#command(String)
     */
    public ProcessConsumer command(String commandLine, Object...arguments) {
    	return command(MessageFormat.format(commandLine, arguments));
    }
    
    /**
     * Crée un processus standard et l'associe à une instance de ProcessConsumer.
     * Le premier paramètre doit obligatoirement correspondre à un nom de programme
     * existant, de la même manière qu'avec l'utilisation de {@linkplain Runtime#exec(String[])}.
     * <br><br>
     * Contrairement à {@link Shell#command(String)}, cette méthode n'instancie
     * pas le processus du shell mais directement le programme passé en
     * premier paramètre.  Le processus ne sera réellement démarré que
     * lors de l'appel d'une des méthodes <code>consume()</code> de la
     * classe ProcessConsumer.<br>
     * @param args Les paramètres de la commandes a exécuter.
     * @return Une instance de ProcessConsumer associé au process.
     */
    public ProcessConsumer exec(String...args) {
    	return new ProcessConsumer(create(args), this.charset);
    }
    
    /**
     * Retourne le nom du shell système.
     * C'est à dire le nom du fichier en local qui
     * return Le nom du shell.
     */
    @Override
    public String toString() {
    	return this.shell[0];
    }
    
    /**
     * Exécute la ligne de commande dans le shell système,
     * en affichant les données dans les flux des sorties de l'application.
     * @param commandLine La ligne de commande à exécuter.
     * @return Le code de retour de la ligne de commande.
     * @throws IOException Erreur d'exécution de la commande.
     * @see System#out
     * @see System#err
     */
    public static int system(String commandLine) throws IOException {
		ProcessBuilder pb = new ProcessBuilder(Shell.SYSTEM_SHELL);
    	pb.command().add(commandLine);
    	return new ProcessConsumer(pb, null).consume();
	}
	
    /**
     * Identique à {@link Shell#system(String)}, si ce n'est que la ligne
     * de commande est d'abord formatté en utilisant les paramètres via
	 * la classe MessageFormat.<br><br>
     * @param commandLine La ligne de commande à exécuter.
     * @return Le code de retour de la ligne de commande.
     * @throws IOException Erreur d'exécution de la commande.
     * @see Shell#system(String)
     */
	public static int system(String commandLine, Object...arguments) throws IOException {
		return system(MessageFormat.format(commandLine, arguments));
	}
	
	/**
     * Exécute le programme spécifié en affichant les données dans
     * les flux des sorties de l'application.
     * @param args Les différents paramètres du programme.
     * @return Le code de retour du programme.
     * @throws IOException Erreur d'exécution du programme.
     * @see System#out
     * @see System#err
     */
	public static int execute(String...args) throws IOException {
		ProcessBuilder pb = new ProcessBuilder(args);
    	return new ProcessConsumer(pb, null).consume();
	}
}
