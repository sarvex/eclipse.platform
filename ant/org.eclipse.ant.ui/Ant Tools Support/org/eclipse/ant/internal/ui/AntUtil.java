/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ant.internal.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.util.FileUtils;
import org.eclipse.ant.core.AntCorePlugin;
import org.eclipse.ant.internal.ui.launchConfigurations.AntHomeClasspathEntry;
import org.eclipse.ant.internal.ui.launchConfigurations.IAntLaunchConfigurationConstants;
import org.eclipse.ant.internal.ui.model.AntElementNode;
import org.eclipse.ant.internal.ui.model.AntModelLite;
import org.eclipse.ant.internal.ui.model.AntProjectNode;
import org.eclipse.ant.internal.ui.model.AntTargetNode;
import org.eclipse.ant.internal.ui.model.IAntModel;
import org.eclipse.ant.internal.ui.model.LocationProvider;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.console.FileLink;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry2;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.console.IHyperlink;

/**
 * General utility class dealing with Ant build files
 */
public final class AntUtil {
	public static final String ATTRIBUTE_SEPARATOR = ","; //$NON-NLS-1$;
	public static final char ANT_CLASSPATH_DELIMITER= '*';
	public static final String ANT_HOME_CLASSPATH_PLACEHOLDER= "G"; //$NON-NLS-1$
	public static final String ANT_GLOBAL_USER_CLASSPATH_PLACEHOLDER= "UG"; //$NON-NLS-1$
	/**
	 * No instances allowed
	 */
	private AntUtil() {
		super();
	}
	
	/**
	 * Returns a single-string of the strings for storage.
	 * 
	 * @param strings the array of strings
	 * @return a single-string representation of the strings or
	 * <code>null</code> if the array is empty.
	 */
	public static String combineStrings(String[] strings) {
		if (strings.length == 0)
			return null;

		if (strings.length == 1)
			return strings[0];

		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < strings.length - 1; i++) {
			buf.append(strings[i]);
			buf.append(ATTRIBUTE_SEPARATOR);
		}
		buf.append(strings[strings.length - 1]);
		return buf.toString();
	}

	/**
	 * Returns an array of targets to be run, or <code>null</code> if none are
	 * specified (indicating the default target should be run).
	 *
	 * @param configuration launch configuration
	 * @return array of target names, or <code>null</code>
	 * @throws CoreException if unable to access the associated attribute
	 */
	public static String[] getTargetNames(ILaunchConfiguration configuration) throws CoreException {
		String attribute = configuration.getAttribute(IAntLaunchConfigurationConstants.ATTR_ANT_TARGETS, (String) null);
		if (attribute == null) {
			return null;
		} 
		return AntUtil.parseRunTargets(attribute);
	}
	
	/**
	 * Returns a map of properties to be defined for the build, or
	 * <code>null</code> if none are specified (indicating no additional
	 * properties specified for the build).
	 *
	 * @param configuration launch configuration
	 * @return map of properties (name --> value), or <code>null</code>
	 * @throws CoreException if unable to access the associated attribute
	 */
	public static Map getProperties(ILaunchConfiguration configuration) throws CoreException {
		Map map = configuration.getAttribute(IAntLaunchConfigurationConstants.ATTR_ANT_PROPERTIES, (Map) null);
		return map;
	}
	
	/**
	 * Returns a String specifying the Ant home to use for the build, or
	 * <code>null</code> if none is specified.
	 *
	 * @param configuration launch configuration
	 * @return String specifying Ant home to use, or <code>null</code>
	 * @throws CoreException if unable to access the associated attribute
	 */
	public static String getAntHome(ILaunchConfiguration configuration) throws CoreException {
		IRuntimeClasspathEntry[] entries = JavaRuntime.computeUnresolvedRuntimeClasspath(configuration);
		for (int i = 0; i < entries.length; i++) {
			IRuntimeClasspathEntry entry = entries[i];
			if (entry.getType() == IRuntimeClasspathEntry.OTHER) {
				IRuntimeClasspathEntry2 entry2 = (IRuntimeClasspathEntry2)entry;
				if (entry2.getTypeId().equals(AntHomeClasspathEntry.TYPE_ID)) {
					return ((AntHomeClasspathEntry)entry2).getAntHome();
				}
			}
		}
		return null;
	}

	/**
	 * Returns an array of property files to be used for the build, or
	 * <code>null</code> if none are specified (indicating no additional
	 * property files specified for the build).
	 *
	 * @param configuration launch configuration
	 * @return array of property file names, or <code>null</code>
	 * @throws CoreException if unable to access the associated attribute
	 */
	public static String[] getPropertyFiles(ILaunchConfiguration configuration) throws CoreException {
		String attribute = configuration.getAttribute(IAntLaunchConfigurationConstants.ATTR_ANT_PROPERTY_FILES, (String) null);
		if (attribute == null) {
			return null;
		}
		String[] propertyFiles= AntUtil.parseString(attribute, ","); //$NON-NLS-1$
		for (int i = 0; i < propertyFiles.length; i++) {
			String propertyFile = propertyFiles[i];
			propertyFile= expandVariableString(propertyFile, AntUIModelMessages.getString("AntUtil.6")); //$NON-NLS-1$ //$NON-NLS-2$
			propertyFiles[i]= propertyFile;
		}
		return propertyFiles;
	}
	
	public static AntTargetNode[] getTargets(String path, ILaunchConfiguration config) throws CoreException {
		File buildfile= getBuildFile(path);
		URL[] urls= getCustomClasspath(config);
		IAntModel model= getAntModel(buildfile, urls);
		model.setCanGetLexicalInfo(false);
		model.setCanGetPositionInfo(false);
		model.setCanGetTaskInfo(true);
		AntProjectNode project= model.getProjectNode();
		List targets= new ArrayList();
		if (project.hasChildren()) {
			Iterator possibleTargets= project.getChildNodes().iterator();
			while (possibleTargets.hasNext()) {
				AntElementNode node= (AntElementNode)possibleTargets.next();
				if (node instanceof AntTargetNode) {
					targets.add(node);
				}
			}
		}
		model.dispose();
		return (AntTargetNode[])targets.toArray(new AntTargetNode[targets.size()]);
	}
	
	public static AntTargetNode[] getTargets(String path) {
		File buildfile= getBuildFile(path);
		if (buildfile == null) {
		    return new AntTargetNode[0];
		}
		IAntModel model= getAntModel(buildfile, null);
		model.setCanGetTaskInfo(true);
		model.setCanGetPositionInfo(true);
		AntProjectNode project= model.getProjectNode();
		List targets= new ArrayList();
		if (project != null && project.hasChildren()) {
			Iterator possibleTargets= project.getChildNodes().iterator();
			while (possibleTargets.hasNext()) {
				AntElementNode node= (AntElementNode)possibleTargets.next();
				if (node instanceof AntTargetNode) {
					targets.add(node);
				}
			}
		}
		
		return (AntTargetNode[])targets.toArray(new AntTargetNode[targets.size()]);
	}
	
	public static IAntModel getAntModel(String buildFilePath, boolean needsTaskResolution, boolean needsLexicalResolution, boolean needsPositionResolution) {
	    IAntModel model= getAntModel(getBuildFile(buildFilePath), null);
	    if (model == null) {
	        return null;
	    }
	    model.setCanGetTaskInfo(needsTaskResolution);
	    model.setCanGetLexicalInfo(needsLexicalResolution);
	    model.setCanGetPositionInfo(needsPositionResolution);
	    return model;   
	}
	
	/**
	 * Return a .xml file from the specified location.
	 * If there isn't one return null.
	 */
	private static File getBuildFile(String path) {
		File buildFile = new File(path);
		if (!buildFile.isFile() || !buildFile.exists()) { 
			return null;
		}

		return buildFile;
	}
	
	private static IAntModel getAntModel(final File buildFile, URL[] urls) {
	    if (buildFile == null || !buildFile.exists()) {
	        return null;
	    }
		IDocument doc= getDocument(buildFile);
		if (doc == null) {
			return null;
		}
		final IFile file= getFileForLocation(buildFile.getAbsolutePath(), null);
		IAntModel model= new AntModelLite(doc, null, new LocationProvider(null) {
		    /* (non-Javadoc)
		     * @see org.eclipse.ant.internal.ui.model.LocationProvider#getFile()
		     */
		    public IFile getFile() {
		        return file;
		    }
			/* (non-Javadoc)
			 * @see org.eclipse.ant.internal.ui.model.LocationProvider#getLocation()
			 */
			public IPath getLocation() {
			    if (file == null) {
			        return new Path(buildFile.getAbsolutePath());   
			    } 
			    return file.getLocation();
			}
		});
		
		if (urls != null) {
		    model.setClassLoader(AntCorePlugin.getPlugin().getNewClassLoader(urls));
		}
		return model;
	}
	
	private static IDocument getDocument(File buildFile) {
		InputStream in;
		try {
			in = new FileInputStream(buildFile);
		} catch (FileNotFoundException e) {
			return null;
		}
		String initialContent= getStreamContentAsString(in);
		return new Document(initialContent);
	}
	
	private static String getStreamContentAsString(InputStream inputStream) {
		InputStreamReader reader;
		try {
			reader = new InputStreamReader(inputStream, ResourcesPlugin.getEncoding());
		} catch (UnsupportedEncodingException e) {
			AntUIPlugin.log(e);
			return ""; //$NON-NLS-1$
		}

		return getReaderContentAsString( new BufferedReader(reader));
	}
	
	private static String getReaderContentAsString(BufferedReader bufferedReader) {
		StringBuffer result = new StringBuffer();
		try {
			String line= bufferedReader.readLine();

			while(line != null) {
				if(result.length() != 0) {
					result.append("\n"); //$NON-NLS-1$
				}
				result.append(line);
				line = bufferedReader.readLine();
			}
		} catch (IOException e) {
			AntUIPlugin.log(e);
			return null;
		}

		return result.toString();
	}
	
	/**
	 * Returns the list of urls that define the custom classpath for the Ant
	 * build, or <code>null</code> if the global classpath is to be used.
	 *
	 * @param config launch configuration
	 * @return a list of <code>URL</code>
	 *
	 * @throws CoreException if file does not exist, IO problems, or invalid format.
	 */
	public static URL[] getCustomClasspath(ILaunchConfiguration config) throws CoreException {
		boolean useDefault = config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, true);
		if (useDefault) {
			return null;
		}
		IRuntimeClasspathEntry[] unresolved = JavaRuntime.computeUnresolvedRuntimeClasspath(config);
		// don't consider bootpath entries
		List userEntries = new ArrayList(unresolved.length);
		for (int i = 0; i < unresolved.length; i++) {
			IRuntimeClasspathEntry entry = unresolved[i];
			if (entry.getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES) {
				userEntries.add(entry);
			}
		}
		IRuntimeClasspathEntry[] entries = JavaRuntime.resolveRuntimeClasspath((IRuntimeClasspathEntry[])userEntries.toArray(new IRuntimeClasspathEntry[userEntries.size()]), config);
		URL[] urls = new URL[entries.length];
		for (int i = 0; i < entries.length; i++) {
			IRuntimeClasspathEntry entry = entries[i];
			try {
				urls[i] = new URL("file:"+entry.getLocation()); //$NON-NLS-1$
			} catch (MalformedURLException e) {
				throw new CoreException(new Status(IStatus.ERROR, AntUIPlugin.getUniqueIdentifier(), AntUIPlugin.INTERNAL_ERROR, AntUIModelMessages.getString("AntUtil.7"), e)); //$NON-NLS-1$
			}
		}
		return urls;		
	}

	private static String expandVariableString(String variableString, String invalidMessage) throws CoreException {
		String expandedString = VariablesPlugin.getDefault().getStringVariableManager().performStringSubstitution(variableString);
		if (expandedString == null || expandedString.length() == 0) {
			String msg = MessageFormat.format(invalidMessage, new String[] {variableString});
			throw new CoreException(new Status(IStatus.ERROR, IAntUIConstants.PLUGIN_ID, 0, msg, null));
		} 
		
		return expandedString;
	}
	
	/**
	 * Returns the list of target names to run
	 * 
	 * @param extraAttibuteValue the external tool's extra attribute value
	 * 		for the run targets key.
	 * @return a list of target names
	 */
	public static String[] parseRunTargets(String extraAttibuteValue) {
		return parseString(extraAttibuteValue, ATTRIBUTE_SEPARATOR);
	}
	
	/**
	 * Returns the list of Strings that were delimiter separated.
	 * 
	 * @param delimString the String to be tokenized based on the delimiter
	 * @return a list of Strings
	 */
	public static String[] parseString(String delimString, String delim) {
		if (delimString == null) {
			return new String[0];
		}
		
		// Need to handle case where separator character is
		// actually part of the target name!
		StringTokenizer tokenizer = new StringTokenizer(delimString, delim);
		String[] results = new String[tokenizer.countTokens()];
		for (int i = 0; i < results.length; i++) {
			results[i] = tokenizer.nextToken();
		}
		
		return results;
	}
	
	/**
	 * Returns an IFile with the given fully qualified path (relative to the workspace root).
	 * The returned IFile may or may not exist.
	 */
	public static IFile getFile(String fullPath) {
		IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
		return root.getFile(new Path(fullPath));
	}

	public static IHyperlink getTaskLink(String path, File buildFileParent) {
		path = path.trim();
		if (path.length() == 0) {
			return null;
		}
		if (path.startsWith("file:")) { //$NON-NLS-1$
			// remove "file:"
			path= path.substring(5, path.length());
		}
		// format is file:F:L: where F is file path, and L is line number
		int index = path.lastIndexOf(':');
		if (index == path.length() - 1) {
			// remove trailing ':'
			path = path.substring(0, index);
			index = path.lastIndexOf(':');
		}
		// split file and line number
		String fileName = path.substring(0, index);
		IFile file = getFileForLocation(fileName, buildFileParent);
		if (file != null) {
			try {
				String lineNumber = path.substring(index + 1);
				int line = Integer.parseInt(lineNumber);
				return new FileLink(file, null, -1, -1, line);
			} catch (NumberFormatException e) {
			}
		}
		return null;
	}

	/**
	 * Returns the workspace file associated with the given path in the
	 * local file system, or <code>null</code> if none.
	 * If the path happens to be a relative path, then the path is interpreted as
	 * relative to the specified parent file.
	 * 
	 * Attempts to handle linked files; the first found linked file with the correct
	 * path is returned.
	 *   
	 * @param path
	 * @param buildFileParent
	 * @return file or <code>null</code>
	 * @see org.eclipse.core.resources.IWorkspaceRoot#findFilesForLocation(IPath)
	 */
	public static IFile getFileForLocation(String path, File buildFileParent) {
		IPath filePath= new Path(path);
		IFile file = null;
		IFile[] files= ResourcesPlugin.getWorkspace().getRoot().findFilesForLocation(filePath);
		if (files.length > 0) {
			file= files[0];
		}
		if (file == null) {
			//relative path
			File relativeFile= null;
			try {
				//this call is ok if buildFileParent is null
				relativeFile= FileUtils.newFileUtils().resolveFile(buildFileParent, path);
				filePath= new Path(relativeFile.getAbsolutePath());
				files= ResourcesPlugin.getWorkspace().getRoot().findFilesForLocation(filePath);
				if (files.length > 0) {
					file= files[0];
				} else {
					return null;
				}
			} catch (BuildException be) {
				return null;
			}
		}
		
		if (file.exists()) {
			return file;
		} 
		File ioFile= file.getLocation().toFile();
		if (ioFile.exists()) {//needs to handle case insensitivity on WINOS
			try {
				files= ResourcesPlugin.getWorkspace().getRoot().findFilesForLocation(new Path(ioFile.getCanonicalPath()));
				if (files.length > 0) {
					return files[0];
				}
			} catch (IOException e) {
			}			
		}
			
		return null;
	}

	/**
	 * Migrates the classpath in the given configuration from the old format
	 * to the new foramt. The old format is not preserved. Instead, the default
	 * classpath will be used. However, ANT_HOME settings are preserved.
	 * 
	 * @param configuration a configuration to migrate
	 * @throws CoreException if unable to migrate
	 * @since 3.0
	 */
	public static void migrateToNewClasspathFormat(ILaunchConfiguration configuration) throws CoreException {
		String oldClasspath = configuration.getAttribute(IAntLaunchConfigurationConstants.ATTR_ANT_CUSTOM_CLASSPATH, (String)null);
		String oldAntHome = configuration.getAttribute(IAntLaunchConfigurationConstants.ATTR_ANT_HOME, (String)null);
		String provider = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER, (String)null);
		if (oldClasspath != null || oldAntHome != null || provider == null) {
			ILaunchConfigurationWorkingCopy workingCopy = null;
			if (configuration.isWorkingCopy()) {
				workingCopy = (ILaunchConfigurationWorkingCopy) configuration;
			} else {
				workingCopy = configuration.getWorkingCopy();
			}
			workingCopy.setAttribute(IAntLaunchConfigurationConstants.ATTR_ANT_CUSTOM_CLASSPATH, (String)null);
			workingCopy.setAttribute(IAntLaunchConfigurationConstants.ATTR_ANT_HOME, (String)null);
			workingCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER, "org.eclipse.ant.ui.AntClasspathProvider"); //$NON-NLS-1$
			workingCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, true);
			if (oldAntHome != null) {
				IRuntimeClasspathEntry[] entries = JavaRuntime.computeUnresolvedRuntimeClasspath(workingCopy);
				List mementos = new ArrayList(entries.length);
				for (int i = 0; i < entries.length; i++) {
					IRuntimeClasspathEntry entry = entries[i];
					if (entry.getType() == IRuntimeClasspathEntry.OTHER) {
						IRuntimeClasspathEntry2 entry2 = (IRuntimeClasspathEntry2) entry;
						if (entry2.getTypeId().equals(AntHomeClasspathEntry.TYPE_ID)) {
							AntHomeClasspathEntry homeEntry = new AntHomeClasspathEntry(oldAntHome);
							mementos.add(homeEntry.getMemento());
							continue;
						}
					}
					mementos.add(entry.getMemento());
				}
				workingCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, false);
				workingCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH, mementos);
			}
			workingCopy.doSave();
		}
	}
}