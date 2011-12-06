/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util.logging;

import bb.gui.FontUtil;
import bb.gui.SwingUtil;
import bb.util.Check;
import bb.util.ThrowableUtil;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.Document;

/**
* Subclass of {@link JFrame} which is used by {@link HandlerGui} to displays the most recent logs.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in the constructor.
* <p>
* @author Brent Boyer
*/
class FrameLog extends JFrame {
	
	// -------------------- constants --------------------
	
	private static final String frameTitle = "FrameLog";
	
	private static final String fontFamilyTextArea = FontUtil.findBestFontFamily("Courier New", "Courier", "Terminal", "FixedSys", "Andale Mono", "Lucida Console", "Monaco", "Monospaced");
	
	private static final String batchSeparator =
		"\n"
		+ "++++++++++++++++++++++++++++++++++++++++++++++++++" + "\n"
		+ "Next batch of LogRecords" + "\n"
		+ "++++++++++++++++++++++++++++++++++++++++++++++++++" + "\n"
		+ "\n";
	
	private static final long serialVersionUID = 1;
	
	// -------------------- instance fields --------------------
	
	/** @serial */
	private final HandlerGui handlerGui;
	
	/** @serial */
	private JTextArea jtextArea;
	
	/** @serial */
	private JLabel statusLabel;
	
	/** @serial */
	private JButton appendButton;
	
	/** @serial */
	private JButton replaceButton;
	
	// -------------------- constructor --------------------
	
	FrameLog(HandlerGui handlerGui) throws IllegalStateException {
		super(frameTitle);
		Check.arg().notNull(handlerGui);
		Check.state().edt();
		
		this.handlerGui = handlerGui;
		
		buildGui();
		closeCustomize();
		setVisible(true);
	}
	
	// -------------------- gui methods --------------------
	
	private void buildGui() {
		getContentPane().removeAll();
		getContentPane().add( buildPanel() );
		SwingUtil.maximizeWindow(this);
	}
	
	private JComponent buildPanel() {
		JPanel jpanel = new JPanel();
		
		JComponent textPart = buildTextPart();
		JComponent statusPart = buildStatusPart();
		JComponent buttonPart = buildButtonPart();
		
		GroupLayout layout = new GroupLayout(jpanel);	// see http://java.sun.com/docs/books/tutorial/uiswing/layout/group.html
		jpanel.setLayout(layout);
		
		layout.setAutoCreateGaps(true);	// automatically add gaps between components
		layout.setAutoCreateContainerGaps(true);	// automatically create gaps between components that touch the edge of the container and the container.
		
		layout.setHorizontalGroup(
			layout.createParallelGroup()
				.addComponent(textPart)
				.addComponent(statusPart)
				.addComponent(buttonPart)
		);
		
		layout.setVerticalGroup(
			layout.createSequentialGroup()
				.addComponent(textPart)
				.addComponent(statusPart)
				.addComponent(buttonPart)
		);
		
		return jpanel;
	}
	
	private JComponent buildTextPart() {
		JScrollPane jscrollPane = new JScrollPane();
		jscrollPane.getViewport().setView( buildTextArea() );
jscrollPane.setPreferredSize( Toolkit.getDefaultToolkit().getScreenSize() );
// +++ added the line above after saw cases where tons of data in the JTextArea caused the preferred size of this JScrollPane to blow up (be many times greater than the screen size) and end up being the only thing drawn on the screen.
// The line above seems to stop such behavior by overruling what the JTextArea is telling the JScrollPane to be.
// This may be due to a bug in GroupLayout:	http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6871357
// See also: http://forums.sun.com/thread.jspa?messageID=10845836#10845836
		return jscrollPane;
	}
	
	private JComponent buildTextArea() {
		jtextArea = new JTextArea( handlerGui.getLogRecords() );
		jtextArea.setEditable(false);
		jtextArea.setFont( new Font(fontFamilyTextArea, Font.PLAIN, 12) );
		jtextArea.setLineWrap(false);
		jtextArea.setTabSize(4);
		return jtextArea;
	}
	
	private JComponent buildStatusPart() {
		statusLabel = new JLabel("The text area above contains all known logs");
		return statusLabel;
	}
	
	private JComponent buildButtonPart() {
		JPanel jpanel = new JPanel();
		
		appendButton = new JButton("Append newest logs");
		appendButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				appendText( batchSeparator );
				appendText( handlerGui.getLogRecords() );
				onLogsNewestDisplayed();
			}
		} );
		appendButton.setEnabled(false);
		getRootPane().setDefaultButton(appendButton);
		
		replaceButton = new JButton("Replace with newest logs");
		replaceButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				jtextArea.setText( handlerGui.getLogRecords() );
				onLogsNewestDisplayed();
			}
		} );
		replaceButton.setEnabled(false);
		
		JButton suppressButton = new JButton("Suppress logs");
		suppressButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				new DialogMsgSuppress( FrameLog.this );
			}
		} );
		
		GroupLayout layout = new GroupLayout(jpanel);	// see http://java.sun.com/docs/books/tutorial/uiswing/layout/group.html
		jpanel.setLayout(layout);
		
		layout.setAutoCreateGaps(true);	// automatically add gaps between components
		layout.setAutoCreateContainerGaps(true);	// automatically create gaps between components that touch the edge of the container and the container.
		
		layout.setHorizontalGroup(
			layout.createSequentialGroup()
				.addComponent(appendButton)
				.addComponent(replaceButton)
				.addComponent(suppressButton)
		);
		
		layout.setVerticalGroup(
			layout.createParallelGroup()
				.addComponent(appendButton)
				.addComponent(replaceButton)
				.addComponent(suppressButton)
		);
		
		return jpanel;
	}
	
	private void appendText(String s) {
		try {
			//jtextArea.setText( jtextArea.getText() + s );	// this line is shorter, but much less efficient, than the lines below...
			Document document = jtextArea.getDocument();
			int offset = document.getLength();
			document.insertString(offset, s, null);
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	private void closeCustomize() {
		//setDefaultCloseOperation( DO_NOTHING_ON_CLOSE );
			// Line above disables the window's close button.
			// In contrast, the lines below do not stop the window from being closed,
			// but they do cause handlerGui's logFrame field to be nulled out.
			// This means that if a new LogRecord subsequently arrives, then the logic inside handlerGui.publish will recreate this window.
		addWindowListener( new WindowAdapter() {
			public void windowClosing(WindowEvent we) { handlerGui.releaseGui(); }
		} );
	}
	
	// -------------------- onLogNewArrived, onLogsNewestDisplayed --------------------
	
	void onLogNewArrived() throws IllegalStateException {
		Check.state().edt();
		
		statusLabel.setText("New log(s) have been published--click a button below to update text area above");
		appendButton.setEnabled(true);
		replaceButton.setEnabled(true);
	}
	
	private void onLogsNewestDisplayed() throws IllegalStateException {
		Check.state().edt();
		
		statusLabel.setText("The text area above contains all known logs");
		appendButton.setEnabled(false);
		replaceButton.setEnabled(false);
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None needed: can be tested by the test classes of other classes, such as HandlerGui
	
}
