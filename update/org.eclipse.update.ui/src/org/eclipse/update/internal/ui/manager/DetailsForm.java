package org.eclipse.update.internal.ui.manager;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
import org.eclipse.update.internal.ui.parts.*;
import org.eclipse.update.internal.ui.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.update.ui.forms.internal.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.graphics.*;

import java.net.URL;
import org.eclipse.update.core.*;
import org.eclipse.update.internal.ui.model.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.custom.*;
import org.eclipse.jface.action.*;
import org.eclipse.ui.*;
import org.eclipse.update.internal.ui.wizards.*;
import org.eclipse.jface.wizard.*;

import java.text.MessageFormat;
import java.util.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.*;
import org.eclipse.jface.dialogs.MessageDialog;
public class DetailsForm extends PropertyWebForm {
// NL keys


private static final String KEY_PROVIDER = "FeaturePage.provider";
private static final String KEY_VERSION = "FeaturePage.version";
private static final String KEY_IVERSION = "FeaturePage.installedVersion";
private static final String KEY_PENDING_VERSION = "FeaturePage.pendingVersion";
private static final String KEY_SIZE = "FeaturePage.size";
private static final String KEY_OS = "FeaturePage.os";
private static final String KEY_WS = "FeaturePage.ws";
private static final String KEY_NL = "FeaturePage.nl";
private static final String KEY_PLATFORMS = "FeaturePage.platforms";
private static final String KEY_DESC = "FeaturePage.description";
private static final String KEY_INFO_LINK = "FeaturePage.infoLink";
private static final String KEY_LICENSE_LINK = "FeaturePage.licenseLink";
private static final String KEY_COPYRIGHT_LINK = "FeaturePage.copyrightLink";
private static final String KEY_NOT_INSTALLED = "FeaturePage.notInstalled";
private static final String KEY_SIZE_VALUE = "FeaturePage.sizeValue";
private static final String KEY_UNKNOWN_SIZE_VALUE = "FeaturePage.unknownSizeValue";
private static final String KEY_DO_UNCONFIGURE="FeaturePage.doButton.unconfigure";
private static final String KEY_DO_CONFIGURE="FeaturePage.doButton.configure";
private static final String KEY_DO_UPDATE="FeaturePage.doButton.update";
private static final String KEY_DO_INSTALL="FeaturePage.doButton.install";
private static final String KEY_OS_WIN32="FeaturePage.os.win32";
private static final String KEY_OS_LINUX="FeaturePage.os.linux";
private static final String KEY_WS_WIN32="FeaturePage.ws.win32";
private static final String KEY_WS_MOTIF="FeaturePage.ws.motif";
private static final String KEY_WS_GTK="FeaturePage.ws.gtk";
private static final String KEY_DIALOG_UTITLE="FeaturePage.dialog.utitle";
private static final String KEY_DIALOG_TITLE="FeaturePage.dialog.title";
private static final String KEY_DIALOG_UMESSAGE="FeaturePage.dialog.umessage";
private static final String KEY_DIALOG_MESSAGE="FeaturePage.dialog.message";
//	
	
private Label imageLabel;
private Label providerLabel;
private Label versionLabel;
private Label installedVersionLabel;
private Label sizeLabel;
private Label osLabel;
private Label wsLabel;
private Label nlLabel;
private Label descriptionText;
private URL infoLinkURL;
private SelectableFormLabel infoLinkLabel;
private InfoLink licenseLink;
private InfoLink copyrightLink;
private ReflowGroup supportedPlatformsGroup;
private Image providerImage;
private Button doButton;
private IFeature currentFeature;
private IFeatureAdapter currentAdapter;
private ModelListener modelListener;
private Hashtable imageCache = new Hashtable();
private HyperlinkHandler sectionHandler;
private boolean alreadyInstalled;

class ModelListener implements IUpdateModelChangedListener {
	/**
	 * @see IUpdateModelChangedListener#objectAdded(Object, Object)
	 */
	public void objectAdded(Object parent, Object child) {
		if (child instanceof PendingChange) {
			PendingChange job = (PendingChange)child;
			if (job.getFeature().equals(currentFeature)) {
				refresh();
			}
		}
	}

	/**
	 * @see IUpdateModelChangedListener#objectRemoved(Object, Object)
	 */
	public void objectRemoved(Object parent, Object child) {
		if (child instanceof PendingChange) {
			PendingChange job = (PendingChange)child;
			if (job.getFeature().equals(currentFeature)) {
				doButton.setEnabled(true);
			}
		}
	}

	/**
	 * @see IUpdateModelChangedListener#objectChanged(Object, String)
	 */
	public void objectChanged(Object object, String property) {
	}
}


abstract class LinkListener implements IHyperlinkListener {
	public abstract URL getURL();
	public void linkActivated(Control linkLabel) {
		URL url = getURL();
		if (url!=null) openURL(url.toString());
	}
	public void linkEntered(Control linkLabel) {
		URL url = getURL();
		if (url!=null)
	 	  showStatus(url.toString());
	}
	public void linkExited(Control linkLabel) {
		showStatus(null);
	}

	private void showStatus(String text) {
		IViewSite site = getPage().getView().getViewSite();
		IStatusLineManager sm = site.getActionBars().getStatusLineManager();
		sm.setMessage(text);
	}
}

abstract class ReflowGroup extends ExpandableGroup {
	public void expanded() {
		reflow();
		updateSize();
	}
	public void collapsed() {
		reflow();
		updateSize();
	}
	protected SelectableFormLabel createTextLabel(Composite parent, FormWidgetFactory factory) {
		SelectableFormLabel label = super.createTextLabel(parent, factory);
		label.setFont(JFaceResources.getBannerFont());
		return label;
	}
	protected HyperlinkHandler getHyperlinkHandler(FormWidgetFactory factory) {
		return sectionHandler;
	}
}

public DetailsForm(UpdateFormPage page) {
	super(page);
	providerImage = UpdateUIPluginImages.DESC_PROVIDER.createImage();
	modelListener = new ModelListener();
	UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
	model.addUpdateModelChangedListener(modelListener);
	sectionHandler = new HyperlinkHandler();
}

public void dispose() {
	UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
	model.removeUpdateModelChangedListener(modelListener);
	providerImage.dispose();
	for (Enumeration enum=imageCache.elements(); enum.hasMoreElements();) {
		Image image = (Image)enum.nextElement();
		image.dispose();
	}
	imageCache.clear();
	sectionHandler.dispose();
	super.dispose();
}
	
public void initialize(Object modelObject) {
	setHeadingText("");
	setHeadingImage(UpdateUIPluginImages.get(UpdateUIPluginImages.IMG_FORM_BANNER));
	setHeadingUnderlineImage(UpdateUIPluginImages.get(UpdateUIPluginImages.IMG_FORM_UNDERLINE));
	super.initialize(modelObject);
}

private void configureSectionHandler(FormWidgetFactory factory) {
	sectionHandler.setHyperlinkUnderlineMode(HyperlinkHandler.UNDERLINE_NEVER);
	sectionHandler.setBackground(factory.getBackgroundColor());
	sectionHandler.setForeground(factory.getColor(factory.COLOR_COMPOSITE_SEPARATOR));
}

public void createContents(Composite container) {
	HTMLTableLayout layout = new HTMLTableLayout();
	layout.numColumns = 2;
	container.setLayout(layout);
	layout.rightMargin = 0;
	GridData gd;
	
	configureSectionHandler(factory);
	
	GridLayout glayout = new GridLayout();
	Composite properties = factory.createComposite(container);
	properties.setLayout(glayout);
	glayout.marginWidth = glayout.marginHeight = 0;
	glayout.verticalSpacing = 0;

	providerLabel = createProperty(properties, UpdateUIPlugin.getResourceString(KEY_PROVIDER));
	versionLabel = createProperty(properties,UpdateUIPlugin.getResourceString(KEY_VERSION));
	installedVersionLabel = createProperty(properties, UpdateUIPlugin.getResourceString(KEY_IVERSION));
	sizeLabel = createProperty(properties, UpdateUIPlugin.getResourceString(KEY_SIZE));
	supportedPlatformsGroup = new ReflowGroup () {
		public void fillExpansion(Composite expansion, FormWidgetFactory factory) {
			GridLayout layout = new GridLayout();
  			expansion.setLayout(layout);
   			layout.marginWidth = 0;
		   	osLabel = createProperty(expansion, UpdateUIPlugin.getResourceString(KEY_OS));
			wsLabel = createProperty(expansion, UpdateUIPlugin.getResourceString(KEY_WS));
			nlLabel = createProperty(expansion, UpdateUIPlugin.getResourceString(KEY_NL));
		}
	};
	supportedPlatformsGroup.setText(UpdateUIPlugin.getResourceString(KEY_PLATFORMS));
	new Label(properties, SWT.NULL);
	supportedPlatformsGroup.createControl(properties, factory);
	
	imageLabel = factory.createLabel(container, null);
	TableData td = new TableData();
	td.align = TableData.CENTER;
	//td.valign = TableData.MIDDLE;
	imageLabel.setLayoutData(td);
	
	Label label = createHeading(container, UpdateUIPlugin.getResourceString(KEY_DESC));
	td = new TableData();
	td.colspan = 2;
	label.setLayoutData(td);
	descriptionText = factory.createLabel(container, null, SWT.WRAP);
	td = new TableData();
	td.colspan = 2;
	td.grabHorizontal = true;
	descriptionText.setLayoutData(td);
	
	glayout = new GridLayout();
	glayout.numColumns = 4;
	glayout.horizontalSpacing = 20;
	glayout.marginWidth = 10;
	
	Composite l = factory.createCompositeSeparator(container);
	l.setBackground(factory.getBorderColor());
	td = new TableData();
	td.colspan = 2;
	td.heightHint = 1;
	td.align = TableData.FILL;
	l.setLayoutData(td);
		
	Composite footer = factory.createComposite(container);
	td = new TableData();
	td.colspan = 2;
	td.align = TableData.FILL;
	td.valign = TableData.FILL;
	footer.setLayoutData(td);
	footer.setLayout(glayout);

	LinkListener listener = new LinkListener() {
		public URL getURL() { return infoLinkURL; }
	};
   	infoLinkLabel = new SelectableFormLabel(footer, SWT.NULL);
   	infoLinkLabel.setText(UpdateUIPlugin.getResourceString(KEY_INFO_LINK));
   	factory.turnIntoHyperlink(infoLinkLabel, listener);
   	gd = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
   	infoLinkLabel.setLayoutData(gd);
   	licenseLink = new InfoLink((DetailsView)getPage().getView());
   	licenseLink.setText(UpdateUIPlugin.getResourceString(KEY_LICENSE_LINK));
   	licenseLink.createControl(footer, factory);
    gd = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
   	licenseLink.getControl().setLayoutData(gd);
   	copyrightLink = new InfoLink((DetailsView)getPage().getView());
   	copyrightLink.setText(UpdateUIPlugin.getResourceString(KEY_COPYRIGHT_LINK));
   	copyrightLink.createControl(footer, factory);
   	gd = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
   	copyrightLink.getControl().setLayoutData(gd);

  	doButton = factory.createButton(footer, "", SWT.PUSH);
  	doButton.addSelectionListener(new SelectionAdapter() {
  		public void widgetSelected(SelectionEvent e) {
  			doButtonSelected();
  		}
  	});
  	gd = new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.VERTICAL_ALIGN_BEGINNING);
  	gd.grabExcessHorizontalSpace = true;
  	doButton.setLayoutData(gd);
}

public void expandTo(final Object obj) {
	BusyIndicator.showWhile(getControl().getDisplay(), new Runnable() {
		public void run() {
			if (obj instanceof IFeature) {
				currentAdapter = null;
				currentFeature = (IFeature)obj;
				refresh();
			}
			else if (obj instanceof IFeatureAdapter) {
				try {
					currentFeature = ((IFeatureAdapter)obj).getFeature();
					currentAdapter = (IFeatureAdapter)obj;
					refresh();
				}
				catch (CoreException e) {
					UpdateUIPlugin.logException(e);
				}
			}
			else {
				currentFeature = null;
				currentAdapter = null;
				refresh();
			}
		}
	});
}

private String getInstalledVersion(IFeature feature) {
	alreadyInstalled = false;
	try {
		ILocalSite localSite = SiteManager.getLocalSite();
	   	IInstallConfiguration config = localSite.getCurrentConfiguration();
	   	IConfigurationSite [] isites = config.getConfigurationSites();
	   	VersionedIdentifier vid = feature.getVersionIdentifier();
	   	String id = vid.getIdentifier();
	   	Version version = vid.getVersion();

	   	StringBuffer buf = new StringBuffer();
	   	for (int i=0; i<isites.length; i++) {
			ISite isite = isites[i].getSite();
			IFeature[] result = UpdateUIPlugin.searchSite(id, isite);
			for (int j=0; j<result.length; j++) {
				IFeature installedFeature = result[j];
				VersionedIdentifier ivid = installedFeature.getVersionIdentifier();
				if (buf.length()>0) 
			   		buf.append(", ");
				buf.append(ivid.getVersion().toString());
				if (ivid.equals(vid)) {
					alreadyInstalled=true;
				}
			}
		}
		if (buf.length()>0) {
			String versionText = buf.toString();
			UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
			PendingChange change = model.findPendingChange(feature);
			if (change!=null) {
				return UpdateUIPlugin.getFormattedMessage(KEY_PENDING_VERSION, versionText);
			}
			else return versionText;
		}
		else
	   		return null;
	}
	catch (CoreException e) {
		return null;
	}
}

private void refresh() {
	boolean newerVersion=false;
	IFeature feature = currentFeature;

	if (feature==null) return;
	
	setHeadingText(feature.getLabel());
	providerLabel.setText(feature.getProvider());
	versionLabel.setText(feature.getVersionIdentifier().getVersion().toString());
	String installedVersion = getInstalledVersion(feature);
	if (installedVersion==null)
	   installedVersion = UpdateUIPlugin.getResourceString(KEY_NOT_INSTALLED);
	else
	   newerVersion = true;
	installedVersionLabel.setText(installedVersion);
	long size = feature.getInstallSize((ISite)null);
	String format = null;
	if (size!=-1){
		String stext = Long.toString(size);
		String pattern = UpdateUIPlugin.getResourceString(KEY_SIZE_VALUE);
		format = UpdateUIPlugin.getFormattedMessage(pattern, stext);
	} else {
		format = UpdateUIPlugin.getResourceString(KEY_UNKNOWN_SIZE_VALUE);
	}
	sizeLabel.setText(format);
	descriptionText.setText(feature.getDescription().getAnnotation());
	Image logoImage = loadProviderImage(feature);
	if (logoImage==null)
	   logoImage = providerImage;
	imageLabel.setImage(logoImage);
	infoLinkURL = feature.getDescription().getURL();
	infoLinkLabel.setVisible(infoLinkURL!=null);

	setOS(feature.getOS());
	setWS(feature.getWS());
	setNL(feature.getNL());
	
	licenseLink.setInfo(feature.getLicense());
	copyrightLink.setInfo(feature.getCopyright());
	doButton.setVisible(getDoButtonVisibility());
	if (doButton.isVisible()) 
		updateButtonText(feature, newerVersion);	
	reflow();
	updateSize();
	((Composite)getControl()).redraw();
}

private boolean getDoButtonVisibility() {
	UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
	if (model.isPending(currentFeature)) return false;
	if (alreadyInstalled) {
		if (!(currentFeature instanceof IConfigurationSiteContext))
			return false;
	}
	return true;
}

private void updateButtonText(IFeature feature, boolean update) {
	if (alreadyInstalled) {
		doButton.setText(UpdateUIPlugin.getResourceString(KEY_DO_UNCONFIGURE));
	}
	else if (update) {
		doButton.setText(UpdateUIPlugin.getResourceString(KEY_DO_UPDATE));
	}
	else
	  	doButton.setText(UpdateUIPlugin.getResourceString(KEY_DO_INSTALL));
}

private Image loadProviderImage(IFeature feature) {
	Image image = null;
	URL imageURL = feature.getImage();
	if (imageURL==null) return null;
	// check table
	image = (Image)imageCache.get(imageURL);
	if (image==null) {
		ImageDescriptor id = ImageDescriptor.createFromURL(imageURL);
		image = id.createImage();
		if (image!=null)
		   imageCache.put(imageURL, image);
	}
	return image;
}

private void reflow() {
	versionLabel.getParent().layout(true);
	doButton.getParent().layout(true);
	imageLabel.getParent().layout(true);
	((Composite)getControl()).layout(true);
}

private void setOS(String os) {
	if (os==null) osLabel.setText("");
	else {
		String [] array = getTokens(os);
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<array.length; i++) {
			if (i>0) buf.append("\n");
			buf.append(mapOS(array[i]));
		}
		osLabel.setText(buf.toString());
	}
}

private String mapOS(String key) {
	if (key.equals("OS_WIN32"))
	   return UpdateUIPlugin.getResourceString(KEY_OS_WIN32);
	if (key.equals("OS_LINUX"))
	   return UpdateUIPlugin.getResourceString(KEY_OS_LINUX);
	return key;
}

private String mapWS(String key) {
	if (key.equals("WS_WIN32"))
	   return UpdateUIPlugin.getResourceString(KEY_WS_WIN32);
	if (key.equals("WS_MOTIF"))
	   return UpdateUIPlugin.getResourceString(KEY_WS_MOTIF);
	if (key.equals("WS_GTK"))
	   return UpdateUIPlugin.getResourceString(KEY_WS_GTK);
	return key;
}

private String mapNL(String nl) {
	String language, country;
	
	int loc = nl.indexOf('_');
	if (loc != -1) {
		language = nl.substring(0, loc);
		country = nl.substring(loc+1);
	}
	else {
		language = nl;
		country = "";
	}
	Locale locale = new Locale(language, country);
	return locale.getDisplayName();
}

private void setWS(String ws) {
	if (ws==null) wsLabel.setText("");
	else {
		String [] array = getTokens(ws);
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<array.length; i++) {
			if (i>0) buf.append("\n");
			buf.append(mapWS(array[i]));
		}
		wsLabel.setText(buf.toString());
	}
}

private void setNL(String nl) {
	if (nl==null) nlLabel.setText("");
	else {
		String [] array = getTokens(nl);
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<array.length; i++) {
			if (i>0) buf.append("\n");
			buf.append(mapNL(array[i]));
		}
		nlLabel.setText(buf.toString());
	}
}

private String [] getTokens(String source) {
	Vector result = new Vector();
	StringTokenizer stok = new StringTokenizer(source, ",");
	while (stok.hasMoreTokens()) {
		String tok = stok.nextToken();
		result.add(tok);
	}
	return (String [])result.toArray(new String[result.size()]);
}

private void openURL(final String url) {
	BusyIndicator.showWhile(getControl().getDisplay(), new Runnable() {
		public void run() {
			DetailsView dv = (DetailsView)getPage().getView();
			dv.showURL(url);
		}
	});
}

private void doButtonSelected() {
	if (currentFeature!=null) {
		int mode = PendingChange.INSTALL;
		if (alreadyInstalled) {
			mode = PendingChange.UNINSTALL;
		}
		final PendingChange job = new PendingChange(currentFeature, mode);
		//UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
		//model.addJob(job);
		BusyIndicator.showWhile(getControl().getDisplay(), new Runnable() {
			public void run() {
				InstallWizard wizard = new InstallWizard(job);
				WizardDialog dialog = new InstallWizardDialog(UpdateUIPlugin.getActiveWorkbenchShell(), wizard);
				dialog.create();
				dialog.getShell().setSize(500, 500);
				dialog.open();
				if (wizard.isSuccessfulInstall()) {
					String title = alreadyInstalled?
					UpdateUIPlugin.getResourceString(KEY_DIALOG_UTITLE):
					UpdateUIPlugin.getResourceString(KEY_DIALOG_TITLE);
					String message=alreadyInstalled?
					UpdateUIPlugin.getResourceString(KEY_DIALOG_UMESSAGE):
					UpdateUIPlugin.getResourceString(KEY_DIALOG_MESSAGE);
					MessageDialog.openInformation(UpdateUIPlugin.getActiveWorkbenchShell(),
							title,
							message);
				}
			}
		});
	}
}
}