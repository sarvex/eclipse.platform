package org.eclipse.update.internal.ui.views;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.update.internal.core.InstallRegistry;
import org.eclipse.update.internal.operations.OperationFactory;
import org.eclipse.update.internal.operations.UpdateUtils;
import org.eclipse.update.internal.ui.UpdateUI;
import org.eclipse.update.internal.ui.UpdateUIMessages;
import org.eclipse.update.internal.ui.model.ConfiguredFeatureAdapter;
import org.eclipse.update.operations.IFeatureOperation;
import org.eclipse.update.operations.IOperation;
import org.eclipse.update.operations.OperationsManager;

public class UnconfigureAndUninstallFeatureAction extends FeatureAction {

	private ConfiguredFeatureAdapter adapter;

	public UnconfigureAndUninstallFeatureAction(Shell shell, String text) {
		super(shell, text);
		setWindowTitle(UpdateUIMessages.FeatureUnconfigureAndUninstallAction_dialogTitle);
	}

	public void run() {
		try {
			IStatus status = OperationsManager.getValidator().validatePlatformConfigValid();
			if (status != null)
				throw new CoreException(status);
			
			if (adapter == null || !confirm(UpdateUIMessages.FeatureUnconfigureAndUninstallAction_question)) 
				return;

			// If current config is broken, confirm with the user to continue
			if (OperationsManager.getValidator().validateCurrentState() != null &&
					!confirm(UpdateUIMessages.Actions_brokenConfigQuestion)) 
				return;

			IOperation operation =
				((OperationFactory)OperationsManager.getOperationFactory()).createUnconfigureAndUninstallFeatureOperation( adapter.getConfiguredSite(), adapter.getFeature(null));

			boolean restartNeeded = operation.execute(null, null);
			UpdateUI.requestRestart(restartNeeded);

		} catch (CoreException e) {
			ErrorDialog.openError(shell, null, null, e.getStatus());
		} catch (InvocationTargetException e) {
			// This should not happen
			UpdateUtils.logException(e.getTargetException());
		}
	}



	public void setSelection(IStructuredSelection selection) {
		
		this.adapter = (ConfiguredFeatureAdapter) selection.getFirstElement();
		setText(UpdateUIMessages.FeatureUnconfigureAndUninstallAction_uninstall); 
	}
	
	
	public boolean canExecuteAction() {
		if (adapter == null)
			return false;
		
		if (!adapter.isConfigured())
			return false;
		
		try {
			// check for pending changes (e.g. if the feature has just been disabled)
			IFeatureOperation pendingOperation = OperationsManager.findPendingOperation(adapter.getFeature(null));
			if (pendingOperation != null)
				return false;

			if (InstallRegistry.getInstance().get("feature_"+adapter.getFeature(null).getVersionedIdentifier()) == null) //$NON-NLS-1$
				return false;
		} catch (CoreException e) {
			return false;
		}
				
		return true;	
	}
}