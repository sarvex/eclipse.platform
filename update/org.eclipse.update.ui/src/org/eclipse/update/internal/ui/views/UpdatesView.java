package org.eclipse.update.internal.ui.views;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
import java.util.*;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.dialogs.PropertyDialogAction;
import org.eclipse.ui.texteditor.IUpdate;
import org.eclipse.update.core.*;
import org.eclipse.update.internal.ui.*;
import org.eclipse.update.internal.ui.model.*;
import org.eclipse.update.internal.ui.parts.DefaultContentProvider;
import org.eclipse.update.internal.ui.search.*;
import org.eclipse.update.internal.ui.security.AuthorizationDatabase;
import org.eclipse.update.internal.ui.wizards.*;

/**
 * Insert the type's description here.
 * @see ViewPart
 */
public class UpdatesView
	extends BaseTreeView
	implements IUpdateModelChangedListener {
	private static final String KEY_NEW = "UpdatesView.Popup.new";
	private static final String KEY_NEW_SITE = "UpdatesView.Popup.newSite";
	private static final String KEY_NEW_FOLDER = "UpdatesView.Popup.newFolder";
	private static final String KEY_NEW_SEARCH = "UpdatesView.Popup.newSearch";
	private static final String KEY_NEW_LOCAL_SITE =
		"UpdatesView.Popup.newLocalSite";
	private static final String KEY_DELETE = "UpdatesView.Popup.delete";
	private static final String KEY_REFRESH = "UpdatesView.Popup.refresh";
	private static final String KEY_FILTER_FILES = "UpdatesView.menu.showFiles";
	private static final String KEY_SHOW_CATEGORIES =
		"UpdatesView.menu.showCategories";
	private static final String KEY_NEW_SEARCH_TITLE = "UpdatesView.newSearch.title";
	private static final String KEY_NEW_BOOKMARK_TITLE = "UpdatesView.newBookmark.title";
	private static final String KEY_NEW_FOLDER_TITLE = "UpdatesView.newFolder.title";
	private Action propertiesAction;
	private Action newAction;
	private Action newFolderAction;
	private Action newSearchAction;
	private Action newLocalAction;
	private DeleteAction deleteAction;
	private Action fileFilterAction;
	private Action showCategoriesAction;
	private Image siteImage;
	private Image featureImage;
	private Image computerImage;
	private Action refreshAction;
	private SearchObject updateSearchObject;
	private SelectionChangedListener selectionListener;
	private Hashtable fileImages = new Hashtable();
	private FileFilter fileFilter = new FileFilter();

	class DeleteAction extends Action implements IUpdate {
		public DeleteAction() {
		}
		public void run() {
			performDelete();
		}
		public void update() {
			boolean enabled = true;
			IStructuredSelection sel =
				(IStructuredSelection) UpdatesView.this.viewer.getSelection();
			for (Iterator iter = sel.iterator(); iter.hasNext();) {
				Object obj = iter.next();
				if (obj instanceof NamedModelObject) {
					if (!(obj instanceof DiscoveryFolder))
						continue;
				}
				enabled = false;
				break;
			}
			setEnabled(enabled);
		}
	}

	class FileFilter extends ViewerFilter {
		public boolean select(Viewer viewer, Object parent, Object child) {
			if (child instanceof MyComputerFile) {
				return false;
			}
			return true;
		}
	}

	class SelectionChangedListener implements ISelectionChangedListener {
		public void selectionChanged(SelectionChangedEvent event) {
			updateForSelection((IStructuredSelection) event.getSelection());
		}
	}

	class SiteProvider
		extends DefaultContentProvider
		implements ITreeContentProvider {

		public Object[] getChildren(Object parent) {
			if (parent instanceof UpdateModel) {
				return getBookmarks((UpdateModel) parent);
			}
			if (parent instanceof BookmarkFolder) {
				return ((BookmarkFolder) parent).getChildren(parent);
			}
			if (parent instanceof SiteBookmark) {
				return getSiteCatalog((SiteBookmark) parent);
			}
			if (parent instanceof SearchObject) {
				return ((SearchObject) parent).getChildren(null);
			}
			if (parent instanceof SearchResultSite) {
				return ((SearchResultSite) parent).getChildren(null);
			}
			if (parent instanceof MyComputer) {
				return ((MyComputer) parent).getChildren(parent);
			}
			if (parent instanceof MyComputerDirectory) {
				return ((MyComputerDirectory) parent).getChildren(parent);
			}
			if (parent instanceof SiteCategory) {
				final SiteCategory category = (SiteCategory) parent;
				BusyIndicator.showWhile(viewer.getTree().getDisplay(), new Runnable() {
					public void run() {
						try {
							category.touchFeatures();
						} catch (CoreException e) {
							UpdateUIPlugin.logException(e);
						}
					}
				});
				return category.getChildren();
			}
			return new Object[0];
		}

		public Object getParent(Object child) {
			return null;
		}

		public boolean hasChildren(Object parent) {
			if (parent instanceof SiteBookmark)
				return true;
			if (parent instanceof MyComputer) {
				return true;
			}
			if (parent instanceof DiscoveryFolder) {
				return true;
			}
			if (parent instanceof BookmarkFolder) {
				return ((BookmarkFolder) parent).hasChildren();
			}
			if (parent instanceof SearchObject) {
				return ((SearchObject) parent).hasChildren();
			}
			if (parent instanceof MyComputerDirectory) {
				return ((MyComputerDirectory) parent).hasChildren(parent);
			}
			if (parent instanceof SiteCategory) {
				return ((SiteCategory) parent).getChildCount() > 0;
			}
			if (parent instanceof SearchResultSite) {
				return ((SearchResultSite) parent).getChildCount() > 0;
			}
			return false;
		}

		public Object[] getElements(Object obj) {
			return getChildren(obj);
		}
	}

	class SiteLabelProvider extends LabelProvider {
		public String getText(Object obj) {
			if (obj instanceof IFeature) {
				IFeature feature = (IFeature) obj;
				return feature.getLabel();
			}
			if (obj instanceof IFeatureAdapter) {
				try {
					IFeature feature = ((IFeatureAdapter) obj).getFeature();
					VersionedIdentifier versionedIdentifier =
						(feature != null) ? feature.getVersionedIdentifier() : null;
					String version = "";
					if (versionedIdentifier != null)
						version = versionedIdentifier.getVersion().toString();
					String label = (feature != null) ? feature.getLabel() : "";
					return label + " " + version;
				} catch (CoreException e) {
					UpdateUIPlugin.logException(e);
				}
			}
			return super.getText(obj);
		}
		public Image getImage(Object obj) {
			if (obj instanceof SiteBookmark || obj instanceof SearchResultSite) {
				return siteImage;
			}
			if (obj instanceof MyComputer) {
				return computerImage;
			}
			if (obj instanceof MyComputerDirectory) {
				return ((MyComputerDirectory) obj).getImage(obj);
			}
			if (obj instanceof MyComputerFile) {
				ImageDescriptor desc = ((MyComputerFile) obj).getImageDescriptor(obj);
				Image image = (Image) fileImages.get(desc);
				if (image == null) {
					image = desc.createImage();
					fileImages.put(desc, image);
				}
				return image;
			}
			if (obj instanceof SiteCategory || obj instanceof BookmarkFolder) {
				return PlatformUI.getWorkbench().getSharedImages().getImage(
					ISharedImages.IMG_OBJ_FOLDER);
			}
			if (obj instanceof SearchObject) {
				return getSearchObjectImage((SearchObject) obj);
			}
			if (obj instanceof IFeature || obj instanceof IFeatureAdapter) {
				return featureImage;
			}
			return super.getImage(obj);
		}
		private Image getSearchObjectImage(SearchObject obj) {
			String categoryId = obj.getCategoryId();
			SearchCategoryDescriptor desc =
				SearchCategoryRegistryReader.getDefault().getDescriptor(categoryId);
			if (desc != null) {
				return desc.getImage();
			}
			return null;
		}
	}

	/**
	 * The constructor.
	 */
	public UpdatesView() {
		UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
		model.addUpdateModelChangedListener(this);
		siteImage = UpdateUIPluginImages.DESC_SITE_OBJ.createImage();
		featureImage = UpdateUIPluginImages.DESC_FEATURE_OBJ.createImage();
		computerImage = UpdateUIPluginImages.DESC_COMPUTER_OBJ.createImage();
		selectionListener = new SelectionChangedListener();
		updateSearchObject = new DefaultUpdatesSearchObject();
	}

	public void dispose() {
		UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
		model.removeUpdateModelChangedListener(this);
		siteImage.dispose();
		featureImage.dispose();
		computerImage.dispose();
		for (Enumeration enum = fileImages.elements(); enum.hasMoreElements();) {
			((Image) enum.nextElement()).dispose();
		}
		super.dispose();
	}

	public void initProviders() {
		viewer.setContentProvider(new SiteProvider());
		viewer.setLabelProvider(new SiteLabelProvider());
		viewer.setInput(UpdateUIPlugin.getDefault().getUpdateModel());
	}

	public void makeActions() {
		super.makeActions();
		propertiesAction =
			new PropertyDialogAction(UpdateUIPlugin.getActiveWorkbenchShell(), viewer);
		newAction = new Action() {
			public void run() {
				performNewBookmark();
			}
		};
		newAction.setText(UpdateUIPlugin.getResourceString(KEY_NEW_SITE));

		newFolderAction = new Action() {
			public void run() {
				performNewBookmarkFolder();
			}
		};
		newFolderAction.setText(UpdateUIPlugin.getResourceString(KEY_NEW_FOLDER));

		newSearchAction = new Action() {
			public void run() {
				performNewSearch();
			}
		};
		newSearchAction.setText(UpdateUIPlugin.getResourceString(KEY_NEW_SEARCH));

		newLocalAction = new Action() {
			public void run() {
				performNewLocal();
			}
		};
		newLocalAction.setText(UpdateUIPlugin.getResourceString(KEY_NEW_LOCAL_SITE));

		deleteAction = new DeleteAction();
		deleteAction.setText(UpdateUIPlugin.getResourceString(KEY_DELETE));

		refreshAction = new Action() {
			public void run() {
				performRefresh();
			}
		};
		refreshAction.setText(UpdateUIPlugin.getResourceString(KEY_REFRESH));

		fileFilterAction = new Action() {
			public void run() {
				if (fileFilterAction.isChecked()) {
					viewer.removeFilter(fileFilter);
				} else
					viewer.addFilter(fileFilter);
			}
		};
		fileFilterAction.setText(UpdateUIPlugin.getResourceString(KEY_FILTER_FILES));
		fileFilterAction.setChecked(false);

		viewer.addFilter(fileFilter);

		showCategoriesAction = new Action() {
			public void run() {
				showCategories(!showCategoriesAction.isChecked());
			}
		};
		showCategoriesAction.setText(
			UpdateUIPlugin.getResourceString(KEY_SHOW_CATEGORIES));
		showCategoriesAction.setChecked(true);

		viewer.addSelectionChangedListener(selectionListener);
	}

	private void updateForSelection(IStructuredSelection selection) {
		refreshAction.setEnabled(selection.size() == 1);
	}

	public void fillActionBars(IActionBars bars) {
		IMenuManager menuManager = bars.getMenuManager();
		menuManager.add(fileFilterAction);
		menuManager.add(new Separator());
		menuManager.add(showCategoriesAction);
		bars.setGlobalActionHandler(IWorkbenchActionConstants.DELETE, deleteAction);
	}

	public void fillContextMenu(IMenuManager manager) {
		Object obj = getSelectedObject();
		manager.add(refreshAction);
		manager.add(new Separator());
		MenuManager newMenu =
			new MenuManager(UpdateUIPlugin.getResourceString(KEY_NEW));
		newMenu.add(newAction);
		newMenu.add(newFolderAction);
		newMenu.add(newSearchAction);
		manager.add(newMenu);
		if (obj instanceof SiteBookmark) {
			SiteBookmark site = (SiteBookmark) obj;
			if (site.getType() == SiteBookmark.LOCAL)
				manager.add(newLocalAction);
		}
		if (canDelete(obj))
			manager.add(deleteAction);
		manager.add(new Separator());
		super.fillContextMenu(manager);
		if (obj instanceof SiteBookmark)
			manager.add(propertiesAction);
	}

	private boolean canDelete(Object obj) {
		if (obj instanceof SiteBookmark) {
			SiteBookmark site = (SiteBookmark) obj;
			return (site.getType() != SiteBookmark.LOCAL);
		}
		if (obj instanceof BookmarkFolder && !(obj instanceof DiscoveryFolder)) {
			return true;
		}
		if (obj instanceof SearchObject
			&& !(obj instanceof DefaultUpdatesSearchObject)) {
			return true;
		}
		return false;
	}

	private Object getSelectedObject() {
		IStructuredSelection sel = (IStructuredSelection) viewer.getSelection();
		return sel.getFirstElement();
	}

	private void performNewBookmark() {
		UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
		Shell shell = UpdateUIPlugin.getActiveWorkbenchShell();
		NewSiteBookmarkWizardPage page =
			new NewSiteBookmarkWizardPage(getSelectedFolder());
		NewWizard wizard = new NewWizard(page, UpdateUIPluginImages.DESC_NEW_BOOKMARK);
		WizardDialog dialog = new WizardDialog(shell, wizard);
		dialog.create();
		dialog.getShell().setText(
			UpdateUIPlugin.getResourceString(KEY_NEW_BOOKMARK_TITLE));
		//dialog.getShell().setSize(400, 400);
		dialog.open();
	}

	private BookmarkFolder getSelectedFolder() {
		Object sel = getSelectedObject();
		if (sel instanceof BookmarkFolder && !(sel instanceof DiscoveryFolder)) {
			BookmarkFolder folder = (BookmarkFolder) sel;
			return folder;
		}
		return null;
	}

	private void performNewBookmarkFolder() {
		UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
		Shell shell = UpdateUIPlugin.getActiveWorkbenchShell();
		NewFolderWizardPage page = new NewFolderWizardPage(getSelectedFolder());
		NewWizard wizard = new NewWizard(page, UpdateUIPluginImages.DESC_NEW_FOLDER);
		WizardDialog dialog = new WizardDialog(shell, wizard);
		dialog.create();
		dialog.getShell().setText(
			UpdateUIPlugin.getResourceString(KEY_NEW_FOLDER_TITLE));
		//dialog.getShell().setSize(400, 350);
		dialog.open();
	}

	private void performNewSearch() {
		UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
		Shell shell = UpdateUIPlugin.getActiveWorkbenchShell();
		NewSearchWizardPage page = new NewSearchWizardPage(getSelectedFolder());
		NewWizard wizard = new NewWizard(page, UpdateUIPluginImages.DESC_NEW_SEARCH);
		WizardDialog dialog = new WizardDialog(shell, wizard);
		dialog.create();
		dialog.getShell().setText(
			UpdateUIPlugin.getResourceString(KEY_NEW_SEARCH_TITLE));
		//dialog.getShell().setSize(400, 350);
		dialog.open();
	}

	private void performNewLocal() {
		ISelection selection = viewer.getSelection();
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ssel = (IStructuredSelection) selection;
			Object obj = ssel.getFirstElement();
			if (obj instanceof SiteBookmark) {
				SiteBookmark bookmark = (SiteBookmark) obj;
				if (bookmark.getType() == SiteBookmark.LOCAL) {
					UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
					Shell shell = UpdateUIPlugin.getActiveWorkbenchShell();
					NewSiteBookmarkWizardPage page =
						new NewSiteBookmarkWizardPage(getSelectedFolder(), bookmark);
					NewWizard wizard = new NewWizard(page, UpdateUIPluginImages.DESC_NEW_BOOKMARK);
					WizardDialog dialog = new WizardDialog(shell, wizard);
					dialog.create();
					dialog.getShell().setText("New Site Bookmark");
					dialog.open();
				}
			}
		}
	}

	private void performDelete() {
		UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
		ISelection selection = viewer.getSelection();
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ssel = (IStructuredSelection) selection;
			if (!confirmDeletion(ssel))
				return;
			for (Iterator iter = ssel.iterator(); iter.hasNext();) {
				Object obj = iter.next();
				if (obj instanceof NamedModelObject) {
					NamedModelObject child = (NamedModelObject) obj;
					BookmarkFolder folder = (BookmarkFolder) child.getParent(child);
					if (folder != null)
						folder.removeChildren(new NamedModelObject[] { child });
					else
						model.removeBookmark(child);
				}
			}
		}
	}

	private void performRefresh() {
		IStructuredSelection sel = (IStructuredSelection) viewer.getSelection();
		final Object obj = sel.getFirstElement();

		if (obj != null) {
			BusyIndicator.showWhile(viewer.getTree().getDisplay(), new Runnable() {
				public void run() {
					try {
						// reinitialize the authenticator 
						AuthorizationDatabase auth = UpdateUIPlugin.getDefault().getDatabase();
						if (auth != null)
							auth.reset();
						if (obj instanceof SiteBookmark)
							 ((SiteBookmark) obj).connect();
						viewer.refresh(obj);
					} catch (CoreException e) {
						UpdateUIPlugin.logException(e);
					}
				}
			});
		}
	}

	private boolean confirmDeletion(IStructuredSelection ssel) {
		String title = "Confirm Delete";
		String message;

		if (ssel.size() > 1) {
			message = "Are you sure you want to delete these " + ssel.size() + " items?";
		} else {
			Object obj = ssel.getFirstElement().toString();
			message = "Are you sure you want to delete \"" + obj.toString() + "\"?";
		}
		return MessageDialog.openConfirm(
			viewer.getControl().getShell(),
			title,
			message);
	}

	private Object[] getBookmarks(UpdateModel model) {
		NamedModelObject[] bookmarks = model.getBookmarks();
		Object[] array = new Object[3 + bookmarks.length];
		array[0] = new MyComputer();
		array[1] = new DiscoveryFolder();
		array[2] = updateSearchObject;
		for (int i = 3; i < array.length; i++) {
			array[i] = bookmarks[i - 3];
		}
		return array;
	}
	public void selectUpdateObject() {
		viewer.setSelection(new StructuredSelection(updateSearchObject), true);
	}

	class CatalogBag {
		Object[] catalog;
	}

	private Object[] getSiteCatalog(final SiteBookmark bookmark) {
		if (!bookmark.isSiteConnected()) {
			final CatalogBag bag = new CatalogBag();
			BusyIndicator.showWhile(viewer.getTree().getDisplay(), new Runnable() {
				public void run() {
					try {
						bookmark.connect();
						bag.catalog = bookmark.getCatalog(showCategoriesAction.isChecked());
					} catch (CoreException e) {
						// FIXME
					}
				}
			});
			if (bag.catalog != null)
				return bag.catalog;
		}
		if (bookmark.getSite() != null) {
			return bookmark.getCatalog(showCategoriesAction.isChecked());
		}
		return new Object[0];
	}

	private void showCategories(boolean show) {
		Object[] expanded = viewer.getExpandedElements();
		for (int i = 0; i < expanded.length; i++) {
			if (expanded[i] instanceof SiteBookmark) {
				viewer.refresh(expanded[i]);
			}
		}
	}

	protected void handleSelectionChanged(SelectionChangedEvent e) {
		deleteAction.update();
	}

	protected void deleteKeyPressed(Widget widget) {
		if (deleteAction.isEnabled())
			deleteAction.run();
	}

	public void objectsAdded(Object parent, Object[] children) {
		Object child = children[0];
		if (child instanceof NamedModelObject
			|| child instanceof SearchResultSite
			|| child instanceof IFeature
			|| child instanceof IFeatureAdapter) {
			UpdateModel model = UpdateUIPlugin.getDefault().getUpdateModel();
			if (parent == null)
				parent = model;
			viewer.add(parent, children);
			if (parent != model)
				viewer.setExpandedState(parent, true);
			viewer.setSelection(new StructuredSelection(children), true);
		}
	}

	public void objectsRemoved(Object parent, Object[] children) {
		if (children[0] instanceof NamedModelObject
			|| children[0] instanceof SearchResultSite) {
			viewer.remove(children);
			viewer.setSelection(new StructuredSelection());
		}
	}

	public void objectChanged(Object object, String property) {
		if (object instanceof SiteBookmark) {
			if (property.equals(SiteBookmark.P_NAME)) {
				viewer.update(object, null);
			}
			if (property.equals(SiteBookmark.P_URL)) {
				viewer.refresh(object);
			}
			viewer.setSelection(viewer.getSelection());
		}
	}

	public void setSelection(IStructuredSelection selection) {
		viewer.setSelection(selection, true);
	}
}