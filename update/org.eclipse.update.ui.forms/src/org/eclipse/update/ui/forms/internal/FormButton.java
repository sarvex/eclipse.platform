/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.update.ui.forms.internal;

import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.graphics.*;

public class FormButton {
	private Button button;
	private Image image;
	private FormWidgetFactory factory;
	private Image hoverImage;
	private boolean inside;
	private Image disabledImage;

public FormButton(Button button, FormWidgetFactory factory) {
	this.button = button;
	this.factory = factory;
	button.addMouseTrackListener(new MouseTrackAdapter() {
		public void mouseEnter(MouseEvent e) {
			inside=true;
			updateImage();
		}
		public void mouseExit(MouseEvent e) {
			inside=false;
			updateImage();
		}
	});
}
public Button getButton() {
	return button;
}
public Image getDisabledImage() {
	return disabledImage;
}
public Image getHoverImage() {
	return hoverImage;
}
public Image getImage() {
	return image;
}
public void setDisabledImage(Image newDisabledImage) {
	disabledImage = newDisabledImage;
}
public void setEnabled(boolean enabled) {
	button.setEnabled(enabled);
	updateImage();
}
public void setHoverImage(Image newHoverImage) {
	hoverImage = newHoverImage;
}
public void setImage(Image newImage) {
	image = newImage;
	if (hoverImage==null) hoverImage = image;
	if (disabledImage==null) disabledImage = image;
	updateImage();
}
public void updateImage() {
	boolean enabled = button.isEnabled();
	if (enabled == false) {
		button.setImage(disabledImage);
	} else {
		if (inside) {
			button.setCursor(factory.getHyperlinkCursor());
			button.setImage(hoverImage);
		} else {
			button.setCursor(null);
			button.setImage(image);
		}
	}
}
}