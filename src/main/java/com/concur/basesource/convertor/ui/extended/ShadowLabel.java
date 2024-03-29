package com.concur.basesource.convertor.ui.extended;

//package mw.client.utils.gui;

import javax.swing.*;
import java.awt.*;

/**
 * 带阴影的标签
 */
public class ShadowLabel extends JLabel {
	private static final long serialVersionUID = -2662918636235396435L;

	private String text;

	private boolean invertColors = false;

	public ShadowLabel() {
		super();
	}

	public ShadowLabel(String text, int size) {
		super();
		this.text = text;
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);

		Graphics2D g2D = (Graphics2D) g;
		// ////////////////////////////////////////////////////////////////
		// antialiasing
		g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		// ////////////////////////////////////////////////////////////////

		/**
		 * draw text
		 */
		if (!invertColors) {
			g2D.setColor(new Color(0, 0, 0));
			g2D.drawString(this.text, 1, 11);
			g2D.setColor(new Color(255, 255, 255, 230));
			g2D.drawString(this.text, 0, 10);
		} else {
			g2D.setColor(new Color(255, 255, 255, 230));
			g2D.drawString(this.text, 1, 11);
			g2D.setColor(new Color(0, 0, 0));
			g2D.drawString(this.text, 0, 10);
		}
		g2D.dispose();

	}

	public void setInvertColors(boolean invertColors) {
		this.invertColors = invertColors;
	}

	public void setText(String text) {
		this.text = text;
		repaint();
	}

}
