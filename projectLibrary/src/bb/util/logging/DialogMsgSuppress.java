/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util.logging;

import bb.gui.LinePanel;
import bb.gui.SwingUtil;
import bb.util.Check;
import bb.util.StringUtil;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
* Subclass of {@link JDialog} which allows the user to manually specify a substring that identifies bad LogRecords.
* <p>
* The user is presented with a JTextArea into which they can type or paste arbitrary text.
* When the dialog is dismissed, the contents of the JTextArea are sent to {@link FilterMsgSuppress#suppressMsg FilterMsgSuppress.suppressMsg}.
* <p>
* Instances are always modal.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in the constructor.
* <p>
* @author Brent Boyer
*/
class DialogMsgSuppress extends JDialog {
	
	private static final long serialVersionUID = 1;
	
	/** @serial */
	private JTextArea jtextArea;
	
	DialogMsgSuppress(Frame owner) throws IllegalStateException {
		super( owner, "DialogMsgSuppress", true );
		// no check on owner: may be null?
		Check.state().edt();
		
		getContentPane().add( buildGui() );
		pack();
// +++ see if still need this after switch to new LinePanel class below...
		setLocationRelativeTo(owner);	// MUST do this call after have properly sized the dialog with pack
		setDefaultCloseOperation( DISPOSE_ON_CLOSE );
		setVisible(true);
	}
	
	private JComponent buildGui() {
		LinePanel linePanel = LinePanel.makeVertical();
		linePanel.add( buildLabel() );
		linePanel.add( buildTextArea() );
		linePanel.add( buildButtons() );
		return linePanel;
	}
	
	private JLabel buildLabel() {
		return new JLabel(
			"<html>" +
				"<p>" +
					"Whatever text that is type into the text area below will be added to a set (if you click on \"Suppress text\")." + "<br/>" +
					"Every Handler which uses FilterMsgSuppress will check future LogRecords against this set:" + "<br/>" +
					"any LogRecord whose message has any element of this set as a substring will be rejected from logging." + "<br/>" +
				"</p>" +
			"</html>"
		);
	}
	
	private JComponent buildTextArea() {
		jtextArea = new JTextArea();
		jtextArea.setEditable(true);
		jtextArea.setLineWrap(true);
		jtextArea.setTabSize(4);
		
		JScrollPane jscrollPane = new JScrollPane();
		jscrollPane.setPreferredSize( SwingUtil.fractionOfScreenSize(0.5, 0.5) );
		jscrollPane.getViewport().setView(jtextArea);
		return jscrollPane;
	}
	
	private JComponent buildButtons() {
		LinePanel linePanel = LinePanel.makeHorizontal();
		
		JButton suppressButton = new JButton("Suppress text");
		suppressButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				String suppress = jtextArea.getText();
				if (!StringUtil.isBlank(suppress)) {
					FilterMsgSuppress.suppressMsg(suppress);
				}
				DialogMsgSuppress.this.dispose();
			}
		} );
		linePanel.add( suppressButton );
		
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				DialogMsgSuppress.this.dispose();
			}
		} );
		getRootPane().setDefaultButton(cancelButton);
		linePanel.add( cancelButton );
		
		return linePanel;
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None needed: can be tested by the test classes of other classes, such as HandlerGui
	
}
