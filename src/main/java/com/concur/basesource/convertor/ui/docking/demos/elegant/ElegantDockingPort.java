package com.concur.basesource.convertor.ui.docking.demos.elegant;

import com.concur.basesource.convertor.ui.docking.defaults.DefaultDockingPort;
import com.concur.basesource.convertor.ui.docking.defaults.StandardBorderManager;
import com.concur.basesource.convertor.ui.extended.RoundedBorder;


public class ElegantDockingPort extends DefaultDockingPort {
	public ElegantDockingPort() {
		setComponentProvider(new ChildComponentDelegate());
		setBorderManager(new StandardBorderManager(new RoundedBorder(10)));
		setOpaque(false);
	}
	
	public void add(ElegantPanel view) {
		dock(view.getDockable(), CENTER_REGION);
	}

	public void add(ElegantPanel view, String position) {
		dock(view.getDockable(), position);
	}

}
