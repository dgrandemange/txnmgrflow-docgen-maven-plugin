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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * ProcessConsumer permet de traiter tous les flux d'un process.<br>
 * Cette classe permet de définir simplement les flux d'E/S associé à un process
 * via les méthodes input()/output()/error(). <br>
 * <br>
 * Par exemple, pour utiliser des fichiers comme flux d'E/S :<br>
 * 
 * <pre><code>
 * ProcessConsumer pc = ...;
 * int result = pc.input( new FileInputStream(&quot;file_stdin.txt&quot;) )
 *   .output( new FileInputStream(&quot;file_stdout.txt&quot;) )
 *   .error( new FileInputStream(&quot;file_stderr.txt&quot;) )
 *   .consume();
 * </code></pre>
 * 
 * Par défaut, aucun flux d'entrée n'est associé, alors que les flux de sorties
 * standard et d'erreur du process sont associé à ceux de l'application Java.
 * 
 * @author adiGuba
 * @version shell-1.0
 */
public class ProcessConsumer {

	/**
	 * L'instance statique de l'executor qui sera chargé de lancer les tâches de
	 * fond.
	 */
	private static final ExecutorService EXECUTOR = Executors
			.newCachedThreadPool(new ThreadFactory() {
				private final ThreadGroup threadGroup = new ThreadGroup(
						"ProcessConsumerThreadGroup");
				private int count = 0;

				public Thread newThread(Runnable runnable) {
					Thread t = new Thread(this.threadGroup, runnable,
							"ProcessConsumerThread-" + (++count));
					t.setPriority(Thread.NORM_PRIORITY);
					t.setDaemon(true);
					return t;
				}
			});

	/** Taille du buffer de lecture lors de la copie des flux. */
	private static final int BUF_SIZE = 8192;
	/** Charset utilisé pour la convertion des flux. */
	private final Charset charset;
	/** Flux d'entrée à rediriger vers le process */
	private Readable stdin = null;
	/** Flux de sortie standard à rediriger depuis le process */
	private Appendable stdout = System.out;
	/** Flux de sortie d'erreur à rediriger depuis le process */
	private Appendable stderr = System.err;

	/** La copie des flux a-t-elle déjà eu lieu. */
	private boolean started = false;
	/** Instance du process (selon le type de constructeur qui est utilisé) */
	private final Process userProcess;
	/**
	 * Instance du processbuilder (selon le type de constructeur qui est
	 * utilisé)
	 */
	private final ProcessBuilder builder;

	/**
	 * Construit un ProcessConsumer pour ce Process, en utilisant le charset par
	 * défaut.
	 * 
	 * @param process
	 *            Le processus qui sera traité.
	 */
	public ProcessConsumer(Process process) {
		this(null, process, null);
	}

	/**
	 * Construit un ProcessConsumer pour ce Process.
	 * 
	 * @param process
	 *            Le processus qui sera traité.
	 * @param cs
	 *            Le charset à utiliser pour la conversion.
	 */
	public ProcessConsumer(Process process, Charset cs) {
		this(null, process, cs);
	}

	/**
	 * Construit un ProcessConsumer pour ce ProcessBuilder, en utilisant le
	 * charset par défaut.
	 * 
	 * @param pb
	 *            Le ProcessBuilder qui sera utilisé pour créer le process.
	 */
	public ProcessConsumer(ProcessBuilder pb) {
		this(pb, null, null);
	}

	/**
	 * Construit un ProcessConsumer pour ce ProcessBuilder.
	 * 
	 * @param pb
	 *            Le ProcessBuilder qui sera utilisé pour créer le process.
	 * @param cs
	 *            Le charset à utiliser pour la conversion.
	 */
	public ProcessConsumer(ProcessBuilder pb, Charset cs) {
		this(pb, null, cs);
	}

	/**
	 * Construit privé contenant tout le code d'initialisation du
	 * ProcessConsumer.
	 * 
	 * @param builder
	 *            Le ProcessBuilder qui sera utilisé pour créer le process.
	 * @param process
	 *            Le processus qui sera traité.
	 * @param cs
	 *            Le charset à utiliser pour la conversion.
	 */
	private ProcessConsumer(ProcessBuilder builder, Process process,
			Charset charset) {
		this.builder = builder;
		this.userProcess = process;
		this.charset = charset != null ? charset : Charset.defaultCharset();
		if (this.builder == null && this.userProcess == null) {
			throw new NullPointerException("null");
		}
	}

	/**
	 * Transforme un InputStream en un objet Readable, en utilisant le charset.
	 * 
	 * @param in
	 *            InputStream (peut être null)
	 * @return un InputStreamReader, ou <b>null</b> si <code>in</code> est
	 *         <b>null</b>.
	 */
	private Readable readable(InputStream in) {
		if (in == null) {
			return null;
		}
		return new InputStreamReader(in, this.charset);
	}

	/**
	 * Transforme un CharSequence en un objet Readable.
	 * 
	 * @param in
	 *            CharSequence (peut être null)
	 * @return un ReadableCharSequence, ou <b>null</b> si <code>in</code> est
	 *         <b>null</b>.
	 */
	private Readable readable(CharSequence in) {
		if (in == null) {
			return null;
		}
		return new ReadableCharSequence(in);
	}

	/**
	 * Définit un objet Readable comme flux d'entrée pour le process. Une seule
	 * des méthodes <code>input()</code> peut être utilisé.<br>
	 * Par défaut, aucun flux d'entrée n'est utilisé.
	 * 
	 * @param in
	 *            Le flux d'entrée du process.
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux d'entrée a déjà été défini précédemment.
	 */
	public ProcessConsumer input(Readable in) throws IllegalStateException {
		if (this.stdin != null) {
			throw new IllegalStateException("INPUT already set.");
		}
		this.stdin = in;
		return this;
	}

	/**
	 * Définit un InputStream comme flux d'entrée pour le process.
	 * 
	 * @param in
	 *            Le flux d'entrée du process.
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux d'entrée a déjà été défini précédemment.
	 * @see ProcessConsumer#input(Readable)
	 */
	public ProcessConsumer input(InputStream in) throws IllegalStateException {
		if (in == System.in) {
			throw new IllegalStateException(
					"System.in cannot be used as 'input'");
		}
		return input(readable(in));
	}

	/**
	 * Définit un CharSequence comme flux d'entrée pour le process.
	 * 
	 * @param in
	 *            Le flux d'entrée du process.
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux d'entrée a déjà été défini précédemment.
	 * @see ProcessConsumer#input(Readable)
	 */
	public ProcessConsumer input(CharSequence in) throws IllegalStateException {
		return input(readable(in));
	}

	/**
	 * Supprime le flux d'entrée pour le process (pas de flux d'entrée).
	 * 
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux d'entrée a déjà été défini précédemment.
	 * @see ProcessConsumer#input(Readable)
	 */
	public ProcessConsumer input() throws IllegalStateException {
		return input((Readable) null);
	}

	/**
	 * Transforme un OutputStream en un objet Appendable, en utilisant le
	 * charset courant.
	 * 
	 * @param out
	 *            OutputStream (peut être null)
	 * @return un OutputStreamWriter, ou <b>null</b> si <code>out</code> est
	 *         <b>null</b>.
	 */
	private Appendable appendable(OutputStream out) {
		if (out == null) {
			return null;
		}
		return new OutputStreamWriter(out, this.charset);
	}

	/**
	 * Définit un objet Appendable comme flux de sortie standard pour le
	 * process. Une seule des méthodes <code>output()</code> peut être
	 * utilisé.<br>
	 * Par défaut, le flux de sortie standard de l'application est utilisé (<code>{@linkplain System#out}</code>).
	 * 
	 * @param out
	 *            Le flux de sortie standard du process.
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux de sortie standard a déjà été défini
	 *             précédemment.
	 */
	public ProcessConsumer output(Appendable out) throws IllegalStateException {
		if (this.stdout != System.out) {
			throw new IllegalStateException("OUTPUT already set.");
		}
		this.stdout = out;
		return this;
	}

	/**
	 * Définit un objet OutputStream comme flux de sortie standard pour le
	 * process.
	 * 
	 * @param out
	 *            Le flux de sortie standard du process.
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux de sortie standard a déjà été défini
	 *             précédemment.
	 * @see ProcessConsumer#output(Appendable)
	 */
	public ProcessConsumer output(OutputStream out)
			throws IllegalStateException {
		return output(appendable(out));
	}

	/**
	 * Définit un objet PrintStream comme flux de sortie standard pour le
	 * process.
	 * 
	 * @param out
	 *            Le flux de sortie standard du process.
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux de sortie standard a déjà été défini
	 *             précédemment.
	 * @see ProcessConsumer#output(Appendable)
	 */
	public ProcessConsumer output(PrintStream out) throws IllegalStateException {
		return output((Appendable) out);
	}

	/**
	 * Supprime le flux de sortie standard pour le process (pas de flux de
	 * sortie).
	 * 
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux de sortie standard a déjà été défini
	 *             précédemment.
	 * @see ProcessConsumer#output(Appendable)
	 */
	public ProcessConsumer output() throws IllegalStateException {
		return output((Appendable) null);
	}

	/**
	 * Définit un objet Appendable comme flux de sortie d'erreur pour le
	 * process. Une seule des méthodes <code>error()</code> peut être utilisé.<br>
	 * Par défaut, le flux de sortie d'erreur de l'application est utilisé (<code>{@linkplain System#err}</code>).
	 * 
	 * @param err
	 *            Le flux de sortie d'erreur du process.
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux de sortie d'erreur a déjà été défini
	 *             précédemment.
	 */
	public ProcessConsumer error(Appendable err) throws IllegalStateException {
		if (this.stderr != System.err) {
			throw new IllegalStateException("ERROR already set.");
		}
		this.stderr = err;
		return this;
	}

	/**
	 * Définit un objet OutputStream comme flux de sortie d'erreur pour le
	 * process.
	 * 
	 * @param err
	 *            Le flux de sortie d'erreur du process.
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux de sortie d'erreur a déjà été défini
	 *             précédemment.
	 * @see ProcessConsumer#error(Appendable)
	 */
	public ProcessConsumer error(OutputStream err) throws IllegalStateException {
		return error(appendable(err));
	}

	/**
	 * Définit un objet PrintStream comme flux de sortie d'erreur pour le
	 * process.
	 * 
	 * @param err
	 *            Le flux de sortie d'erreur du process.
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux de sortie d'erreur a déjà été défini
	 *             précédemment.
	 * @see ProcessConsumer#error(Appendable)
	 */
	public ProcessConsumer error(PrintStream err) throws IllegalStateException {
		return error((Appendable) err);
	}

	/**
	 * Supprime le flux de sortie d'erreur pour le process (pas de flux de
	 * sortie).
	 * 
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux de sortie d'erreur a déjà été défini
	 *             précédemment.
	 * @see ProcessConsumer#error(Appendable)
	 */
	public ProcessConsumer error() throws IllegalStateException {
		return error((Appendable) null);
	}

	/**
	 * Définit un objet PrintStream comme flux de sortie d'erreur pour le
	 * process. Cette méthode ne peut être utilisé qu'avec une instance de
	 * ProcessBuilder.
	 * 
	 * @return <b>this</b>
	 * @throws IllegalStateException
	 *             Lorsque le flux de sortie d'erreur a déjà été défini
	 *             précédemment, ou que cet instance de ProcessConsumer ne
	 *             possède pas de ProcessBuilder.
	 * @see ProcessConsumer#error(Appendable)
	 * @see ProcessConsumer#ProcessConsumer(ProcessBuilder)
	 * @see ProcessConsumer#ProcessConsumer(ProcessBuilder, Charset)
	 */
	public ProcessConsumer errorRedirect() throws IllegalStateException {
		if (this.builder == null) {
			throw new IllegalStateException("No ProcessBuilder");
		}
		error();
		this.builder.redirectErrorStream(true);
		return this;
	}

	/**
	 * Retourne le process à utiliser selon le constructeur utilisé. Soit cette
	 * méthode retourne le process passé au constructeur, soit elle démarre un
	 * nouveau process via le ProcessBuilder
	 * 
	 * @return Le process
	 * @throws IOException
	 *             Si le process a déjà été "démarré"
	 */
	private Process getProcess() throws IOException {
		if (this.started) {
			throw new IOException("Process already started");
		}
		if (this.builder == null) {
			return this.userProcess;
		}
		return this.builder.start();
	}

	/**
	 * Consume tous les flux du process en associant les différents flux. Cette
	 * méthode est bloquante tant que le process n'est pas terminé.
	 * 
	 * @return Le code de retour du process.
	 * @see Process#exitValue()
	 * @throws IOException
	 *             Erreur d'E/S
	 */
	public int consume() throws IOException {
		Future<?> inputTask = null;
		Future<?> errorTask = null;
		Process process = getProcess();
		try {
			OutputStream pIn = process.getOutputStream();
			if (this.stdin == null) {
				pIn.close();
			} else {
				inputTask = dumpInBackground(this.stdin, appendable(pIn));
			}

			InputStream pErr = process.getErrorStream();
			if (this.stderr == null) {
				pErr.close();
			} else {
				errorTask = dumpInBackground(readable(pErr), this.stderr);
			}

			InputStream pOut = process.getInputStream();
			if (this.stdout == null) {
				pOut.close();
			} else {
				dump(readable(pOut), this.stdout);
			}

			try {
				return process.waitFor();
			} catch (InterruptedException e) {
				IOException ioe = new InterruptedIOException();
				ioe.initCause(e);
				throw ioe;
			}
		} finally {
			process.destroy();
			if (inputTask != null) {
				inputTask.cancel(true);
			}
			if (errorTask != null) {
				errorTask.cancel(true);
			}
		}
	}

	/**
	 * Consume tous les flux du process en associant les différents flux, et
	 * redirige la sortie standard vers une chaine de caractère. Cette méthode
	 * est bloquante tant que le process n'est pas terminé.
	 * 
	 * @return Une chaine de caractère contenant la sortie standard du process.
	 * @throws IOException
	 *             Erreur d'E/S
	 * @throws IllegalStateException
	 *             Lorsque le flux de sortie standard a déjà été défini
	 *             précédemment.
	 */
	public String consumeAsString() throws IOException, IllegalStateException {
		StringBuilder builder = new StringBuilder();
		output(builder).consume();
		return builder.toString();
	}

	/**
	 * Consume tous les flux du process en associant les différents flux. Cette
	 * méthode se contente d'exécuter la méthode
	 * {@link ProcessConsumer#consume()} en tâche de fond.
	 * 
	 * @return L'objet Future permettant de manipuler la tâche de fond.
	 * @see Future
	 */
	public Future<Integer> consumeInBackground() {
		return ProcessConsumer.inBackground(new Callable<Integer>() {
			public Integer call() throws Exception {
				return ProcessConsumer.this.consume();
			}
		});
	}

	/**
	 * Consume tous les flux du process en associant les différents flux, et
	 * redirige la sortie standard vers une chaine de caractère. Cette méthode
	 * se contente d'exécuter la méthode
	 * {@link ProcessConsumer#consumeAsString()} en tâche de fond.
	 * 
	 * @return L'objet Future permettant de manipuler la tâche de fond.
	 * @see Future
	 */
	public Future<String> consumeAsStringInBackground() {
		return ProcessConsumer.inBackground(new Callable<String>() {
			public String call() throws Exception {
				return ProcessConsumer.this.consumeAsString();
			}
		});
	}

	/**
	 * Tente de fermer l'objet en paramètre.
	 * 
	 * @param c
	 *            L'objet à fermer.
	 */
	private void tryToClose(Object c) {
		if (c instanceof Closeable && c != System.in && c != System.out
				&& c != System.err) {
			try {
				((Closeable) c).close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (c == this.stdin) {
				this.stdin = null;
			} else if (c == this.stdout) {
				this.stdout = null;
			} else if (c == this.stderr) {
				this.stderr = null;
			}
		}
	}

	/**
	 * Garde fou qui force la fermeture des flux au cas où...
	 */
	@Override
	protected void finalize() {
		tryToClose(this.stdin);
		tryToClose(this.stdout);
		tryToClose(this.stderr);
	}

	/**
	 * Copie des flux de <code>in</code> vers <code>out</code>.
	 * 
	 * @param in
	 *            Flux depuis lequel les données seront lues
	 * @param out
	 *            Flux vers lequel les données seront écrites
	 * @throws IOException
	 *             Erreur E/S
	 */
	private final void dump(Readable in, Appendable out) throws IOException {
		try {
			try {
				Flushable flushable = null;
				if (out instanceof Flushable) {
					flushable = ((Flushable) out);
				}
				Thread current = Thread.currentThread();
				CharBuffer cb = CharBuffer.allocate(BUF_SIZE);
				int len;

				cb.clear();
				while (!current.isInterrupted() && (len = in.read(cb)) > 0
						&& !current.isInterrupted()) {
					cb.position(0).limit(len);
					out.append(cb);
					cb.clear();
					if (flushable != null) {
						flushable.flush();
					}
				}
			} finally {
				tryToClose(in);
			}
		} finally {
			tryToClose(out);
		}
	}

	/**
	 * Copie des flux de <code>in</code> vers <code>out</code>, en tâche de
	 * fond.
	 * 
	 * @param in
	 *            Flux depuis lequel les données seront lues
	 * @param out
	 *            Flux vers lequel les données seront écrites
	 */
	public final Future<Void> dumpInBackground(final Readable in,
			final Appendable out) {
		return ProcessConsumer.inBackground(new Callable<Void>() {
			public Void call() throws Exception {
				dump(in, out);
				return null;
			}
		});
	}

	/**
	 * Exécute une tâche dans un thread séparée, en utilisant un pool de thread.
	 * 
	 * @param <T>
	 *            Le type du résultat de la tâche.
	 * @param task
	 *            La tâche a exécuter.
	 * @return L'objet Future permettant de manipuler la tâche.
	 * @see Future
	 */
	protected static <T> Future<T> inBackground(Callable<T> task) {
		return ProcessConsumer.EXECUTOR.submit(task);
	}
}
