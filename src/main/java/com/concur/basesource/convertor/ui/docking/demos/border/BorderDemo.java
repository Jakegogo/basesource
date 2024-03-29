package com.concur.basesource.convertor.ui.docking.demos.border;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import com.concur.basesource.convertor.ui.docking.DockingPort;
import com.concur.basesource.convertor.ui.docking.defaults.ComponentProviderAdapter;
import com.concur.basesource.convertor.ui.docking.defaults.DefaultDockingPort;

public class BorderDemo extends JFrame {
	
	public BorderDemo() {
		super("Border Docking Demo");
		setContentPane(createContentPane());
	}
	
	private JPanel createContentPane() {
		JPanel p = new JPanel(new BorderLayout(5, 5));
		p.add(buildDockingPort("North"), BorderLayout.NORTH);
		p.add(buildDockingPort("South"), BorderLayout.SOUTH);
		p.add(buildDockingPort("East"), BorderLayout.EAST);
		p.add(buildDockingPort("West"), BorderLayout.WEST);
		p.add(createDockingPort(), BorderLayout.CENTER);
		return p;
	}
	
	private DefaultDockingPort buildDockingPort(String desc) {
		// create the DockingPort
		DefaultDockingPort port = createDockingPort();

		// create the Dockable panel
		DockablePanel panel = new DockablePanel(desc);

		// dock the panel and return the DockingPort
		port.dock(panel.getDockable(), DockingPort.CENTER_REGION);
		return port;
	}
	
	private DefaultDockingPort createDockingPort() {
		DefaultDockingPort port = new DefaultDockingPort();
		port.setPreferredSize(new Dimension(100, 100));
		port.setBorderManager(new DemoBorderManager());
		port.setComponentProvider(new ComponentProvider());
		return port;
	}

	private class ComponentProvider extends ComponentProviderAdapter {
		public DockingPort createChildPort() {
			DefaultDockingPort port = new DefaultDockingPort();
			port.setComponentProvider(this);
			return port;
		}
		public JSplitPane createSplitPane() {
			JSplitPane split = new JSplitPane();
			split.setDividerSize(3);
			split.setBorder(null);
			if (split.getUI() instanceof BasicSplitPaneUI) {
				BasicSplitPaneDivider divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
				if (divider != null && divider.getBorder()!=null)
					divider.setBorder(null);			
			}
			return split;
		}
	}



	public static void main(String[] args) {
		JFrame f = new BorderDemo();
		f.setSize(600, 400);
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.setVisible(true);
	}
}
