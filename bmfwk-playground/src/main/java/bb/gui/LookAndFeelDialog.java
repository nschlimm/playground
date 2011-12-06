/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;

import bb.util.Check;
import bb.util.Execute;
import bb.util.logging.LogUtil;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
* Subclass of JDialog that allows the user to choose the Swing Look and Feel.
* The Look and Feels presented to the user are determined by {@link LookAndFeelUtil}.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @see LookAndFeelUtil
* @author Brent Boyer
*/
public class LookAndFeelDialog extends JDialog {
	
	// -------------------- constants --------------------
	
	private static final String dialogTitle = "Look And Feel Chooser";
	
	private static final String lookAndFeel_default = "Metal";
	
	private static final long serialVersionUID = 1;
	
	// -------------------- instance Fields --------------------
	
	/**
	* The {@link Component} whose Look and Feel will be changed by this instance.
	* @serial
	*/
	private final Component target;
	
	/**
	* ButtonGroup for all the Look and Feel choices.
	* @serial
	*/
	private final ButtonGroup buttonGroup = new ButtonGroup();
	
	// -------------------- constructor --------------------
	
	/**
	* Constructs a new LookAndFeelDialog instance.
	* <p>
	* @param parent the parent Frame that this JDialog will be attached to; may be null
	* @param modal specifies whether or not this JDialog is a modal dialog
	* @param target specifies the Object that is to update its Look And Feel for the new choice
	* @throws IllegalArgumentException if target is null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public LookAndFeelDialog(Frame parent, boolean modal, Component target) throws IllegalArgumentException, IllegalStateException {
		super(parent, dialogTitle, modal);
		Check.arg().notNull(target);
		Check.state().edt();
		
		this.target = target;
		
		this.setDefaultCloseOperation( DISPOSE_ON_CLOSE );
		this.getContentPane().add( buildGui() );
		this.pack();
		this.setLocationRelativeTo(parent);	// MUST do this call after have properly sized the dialog with pack
		this.setVisible(true);
	}
	
	// -------------------- build methods --------------------
	
	protected JPanel buildGui() {
		LinePanel linePanel = LinePanel.makeVertical();
		linePanel.add( new JLabel("Please select a Look and Feel:") );
		linePanel.add( buildLafChoices() );
		linePanel.add( buildButtons() );
		return linePanel;
	}
	
	protected JPanel buildLafChoices() {
		LinePanel linePanel = LinePanel.makeVertical();
		for (String lafName : LookAndFeelUtil.lookAndFeels) {
			JRadioButton button = new JRadioButton(lafName);
			button.setActionCommand(lafName);
			//preselectIfIsDefault(button);
			preselectIfIsCurrent(button);
			buttonGroup.add( button );
			linePanel.add( button );
		}
		return linePanel;
	}
	
	protected void preselectIfIsDefault(JRadioButton button) {
		if ( button.getActionCommand().equals( lookAndFeel_default ) ) {
			button.setSelected(true);
		}
	}
	
	protected void preselectIfIsCurrent(JRadioButton button) {
		if ( button.getActionCommand().equals( UIManager.getLookAndFeel().getName() ) ) {
			button.setSelected(true);
		}
	}
	
	protected JPanel buildButtons() {
		LinePanel linePanel = LinePanel.makeHorizontal();
		
		JButton okButton = new JButton("OK");
		okButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				try {
					String lafName = buttonGroup.getSelection().getActionCommand();
					String lafClassName = LookAndFeelUtil.getClassName(lafName);
					updateLaf( lafClassName );
				}
				catch (Throwable t) {
					LogUtil.getLogger2().logp(Level.SEVERE, "LookAndFeelDialog.okButton.ActionListener", "actionPerformed", "Failed to change the Look and Feel to the value just selected", t);
					new ThrowableDialog(LookAndFeelDialog.this, "Error", true, "Failed to change the Look and Feel to the value just selected", t);
				}
				finally {
					LookAndFeelDialog.this.dispose();
				}
			}
		} );
		this.getRootPane().setDefaultButton(okButton);
		linePanel.add( okButton );
		
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				LookAndFeelDialog.this.dispose();
			}
		} );
		linePanel.add( cancelButton );
		
		return linePanel;
	}
	
	protected void updateLaf(String lafClassName) throws Exception {
		UIManager.setLookAndFeel( lafClassName );
		SwingUtilities.updateComponentTreeUI( target );
//		target.invalidate();
//		target.validate();
//		target.repaint();
// +++ lines above don't seem to be needed, but keep around in case every see a problem in future...
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		public static void main(final String[] args) {
			Execute.usingEdt( new Runnable() { public void run() {
				Check.arg().empty(args);
				
				Frame target = LinePanel.UnitTest.buildGui();
				new LookAndFeelDialog(target, false, target);
			} } );
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
