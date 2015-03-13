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
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;

/**
 * Cette classe permet simplement d'encapsuler un CharSequence
 * afin de l'utiliser comme un Readable.
 * 
 * @see Readable
 * @see CharSequence
 * 
 * @author adiGuba
 * @version shell-1.0
 */
public class ReadableCharSequence implements Readable, Closeable {

	/** Le CharSequence depuis lequel les données seront lues. */
	private final CharSequence cs;
	/** Position courante dans le CharSequence. */
	private int pos = 0;
	
	/**
	 * Construit un ReadableCharSequence qui lit les données
	 * dans l'objet CharSequence en paramètre. 
	 * @param cs Le CharSequence source des données.
	 */
	public ReadableCharSequence(CharSequence cs) {
		this.cs = cs;
		if (this.cs==null) {
			throw new NullPointerException("null");
		}
	}
	
	/**
	 * Ferme le flxu courant, et evenutellement le
	 * CharSequence si celui-ci implémente Closeable.
	 * @see Closeable
	 */
	public void close() throws IOException {
		if (this.cs instanceof Closeable) {
			((Closeable)this.cs).close();
		}
		this.pos = -1;
	}

	/**
	 * Lit lesflux depuis le CharSequence vers le CharBuffer en paramètre.
	 */
	public int read(CharBuffer cb) throws IOException {
		if (this.pos < 0) {
			throw new ClosedChannelException();
		}
		int remaining = this.cs.length()-pos;
		if (remaining>0) {
			int len = Math.min( cb.remaining() , remaining);
			cb.append(this.cs, pos, len);
			this.pos += len;
			return len;
		}
		return -1;
	}
}
