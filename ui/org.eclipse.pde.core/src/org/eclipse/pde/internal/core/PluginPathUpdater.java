package org.eclipse.pde.internal.core;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.File;
import java.util.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.pde.core.build.*;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.core.plugin.ExternalPluginModelBase;

public class PluginPathUpdater {
	public static final String KEY_UPDATING = "PluginPathUpdater.updating";
	public static final String JRE_VAR = "JRE_LIB";
	public static final String JRE_SRCVAR = "JRE_SRC";
	public static final String JRE_SRCROOTVAR = "JRE_SRCROOT";
	private Iterator checkedPlugins;
	private boolean relative=true;

	public static class PluginEntry {
		private IPlugin plugin;
		private boolean exported;
		public PluginEntry(IPlugin plugin) {
			this.plugin = plugin;
		}
		public IPlugin getPlugin() {
			return plugin;
		}
		public void setExported(boolean exported) {
			this.exported = exported;
		}
		public boolean isExported() {
			return exported;
		}
	}

	public PluginPathUpdater(Iterator checkedPlugins) {
		this.checkedPlugins = checkedPlugins;
	}
	public PluginPathUpdater(Iterator checkedPlugins, boolean relative) {
		this(checkedPlugins);
		this.relative = relative;
	}
	private void addFoldersToClasspathEntries(
		IPluginModelBase model,
		Vector result) {
		IFile file = (IFile) model.getUnderlyingResource();
		IPath buildPath =
			file.getProject().getFullPath().append("build.properties");
		IFile buildFile = file.getWorkspace().getRoot().getFile(buildPath);
		if (!buildFile.exists())
			return;
		WorkspaceModelManager manager =
			PDECore.getDefault().getWorkspaceModelManager();
		manager.connect(buildFile, null, false);
		IBuildModel buildModel =
			(IBuildModel) manager.getModel(buildFile, null);
		IBuild build = buildModel.getBuild();
		IBuildEntry[] entries = build.getBuildEntries();
		for (int i = 0; i < entries.length; i++) {
			IBuildEntry entry = entries[i];
			if (!entry.getName().startsWith("source."))
				continue;
			String[] tokens = entry.getTokens();
			for (int j = 0; j < tokens.length; j++) {
				String folderName = tokens[j];
				IPath folderPath =
					file.getProject().getFullPath().append(folderName);
				if (file.getWorkspace().getRoot().exists(folderPath)) {
					result.add(JavaCore.newSourceEntry(folderPath));
				}
			}
		}
		manager.disconnect(buildFile, null);
	}
	private static void addToClasspathEntries(
		PluginEntry element,
		boolean relative,
		Vector result) {
		IPlugin plugin = element.getPlugin();
		IPluginModelBase model = plugin.getModel();
		boolean internal = model.getUnderlyingResource() != null;

		if (internal) {
			IPath projectPath =
				model.getUnderlyingResource().getProject().getFullPath();
			if (!isEntryAdded(projectPath,
				IClasspathEntry.CPE_PROJECT,
				result)) {
				IClasspathEntry projectEntry =
					JavaCore.newProjectEntry(projectPath, element.isExported());
				result.addElement(projectEntry);
			}
			return;
		}
		IPath modelPath;
		
		if (relative) modelPath = getExternalPath(model);
		else modelPath = new Path(model.getInstallLocation());

		IPluginLibrary[] libraries = plugin.getLibraries();

		for (int i = 0; i < libraries.length; i++) {
			IPluginLibrary library = libraries[i];

			String name = expandLibraryName(library.getName());
			IPath libraryPath = modelPath.append(name);
			IPath[] sourceAnnot = getSourceAnnotation(plugin, modelPath, name);
			int entryKind = relative ? IClasspathEntry.CPE_VARIABLE : IClasspathEntry.CPE_LIBRARY;
			if (!isEntryAdded(libraryPath,
				entryKind,
				result)) {
				IClasspathEntry libraryEntry =
					relative? 
					JavaCore.newVariableEntry(
						libraryPath,
						sourceAnnot[0],
						sourceAnnot[1]) :
					JavaCore.newLibraryEntry(
						libraryPath,
						sourceAnnot[0],
						sourceAnnot[1]);
				IClasspathEntry resolved =
					relative ? 
					JavaCore.getResolvedClasspathEntry(libraryEntry) : libraryEntry;
				if (resolved != null && resolved.getPath().toFile().exists())
					result.addElement(libraryEntry);
				else if (!(model instanceof IFragmentModel)) {
					// cannot find this entry - try to locate it 
					// in one of the fragments
					libraryEntry =
						getFragmentEntry(
							(IPluginModel) model,
							name,
							relative,
							element.isExported());
					if (libraryEntry != null
						&& !isEntryAdded(libraryEntry.getPath(),
							entryKind,
							result)) {
						result.addElement(libraryEntry);
					}
				}
			}
		}
		// Recursively add reexported libraries
		IPluginImport[] imports = plugin.getImports();
		for (int i = 0; i < imports.length; i++) {
			IPluginImport iimport = imports[i];
			// don't recurse if the library is not reexported
			if (iimport.isReexported() == false)
				continue;
			String id = iimport.getId();
			IPlugin reference = PDECore.getDefault().findPlugin(id);
			if (reference != null) {
				PluginEntry ref = new PluginEntry(reference);
				addToClasspathEntries(ref, relative, result);
			}
		}
	}

	private static IClasspathEntry getFragmentEntry(
		IPluginModel model,
		String name,
		boolean relative,
		boolean exported) {
		IFragmentModel[] fragments =
			PDECore.getDefault().getExternalModelManager().getFragmentsFor(
				model);
		for (int i = 0; i < fragments.length; i++) {
			IFragmentModel fmodel = fragments[i];
			IPath modelPath;
			if (relative) modelPath = getExternalPath(fmodel);
			else modelPath = new Path(fmodel.getInstallLocation());
			IPath libraryPath = modelPath.append(name);
			IPath[] sourceAnnot =
				getSourceAnnotation(fmodel.getFragment(), modelPath, name);
			IClasspathEntry libraryEntry =
				relative ? 
				JavaCore.newVariableEntry(
					libraryPath,
					sourceAnnot[0],
					sourceAnnot[1],
					exported):
				JavaCore.newLibraryEntry(
					libraryPath,
					sourceAnnot[0],
					sourceAnnot[1]);
			IClasspathEntry resolved = relative?
				JavaCore.getResolvedClasspathEntry(libraryEntry):libraryEntry;
			if (resolved != null && resolved.getPath().toFile().exists()) {
				// looks good - return it
				return libraryEntry;
			}
		}
		return null;
	}

	public static IPath getExternalPath(IPluginModelBase model) {
		IPath modelPath = new Path(PDECore.ECLIPSE_HOME_VARIABLE);
		modelPath =
			modelPath.append(
				((ExternalPluginModelBase) model).getEclipseHomeRelativePath());
		return modelPath;
	}

	public static IClasspathEntry createLibraryEntry(
		IPluginLibrary library,
		IPath rootPath,
		boolean unconditionallyExport) {
		String name = expandLibraryName(library.getName());
		boolean variable = rootPath.segment(0).equals("ECLIPSE_HOME");
		IPath libraryPath = rootPath.append(name);
		IPath[] sourceAnnot =
			getSourceAnnotation(library.getPluginBase(), rootPath, name);
		if (variable)
			return JavaCore.newVariableEntry(
				libraryPath,
				sourceAnnot[0],
				sourceAnnot[1],
				unconditionallyExport ? true : library.isFullyExported());
		else
			return JavaCore.newLibraryEntry(
				libraryPath,
				sourceAnnot[0],
				sourceAnnot[1],
				unconditionallyExport ? true : library.isFullyExported());
	}

	private static boolean isEntryAdded(IPath path, int kind, Vector entries) {
		for (int i = 0; i < entries.size(); i++) {
			IClasspathEntry entry = (IClasspathEntry) entries.elementAt(i);
			if (entry.getEntryKind() == kind) {
				if (entry.getPath().equals(path))
					return true;
			}
		}
		return false;
	}

	public void addClasspathEntries(Vector result) {
		for (Iterator iter = checkedPlugins; iter.hasNext();) {
			PluginEntry element = (PluginEntry) iter.next();
			addToClasspathEntries(element, relative, result);
		}
	}

	public IClasspathEntry[] getClasspathEntries() {
		Vector result = new Vector();
		addClasspathEntries(result);
		IClasspathEntry[] finalEntries = new IClasspathEntry[result.size()];
		result.copyInto(finalEntries);
		return finalEntries;
	}
	public static IPath getJREPath() {
		return JavaCore.getClasspathVariable(JRE_VAR);
	}
	public static IPath[] getJRESourceAnnotation() {
		IPath source = JavaCore.getClasspathVariable(JRE_SRCVAR);
		IPath prefix = JavaCore.getClasspathVariable(JRE_SRCROOTVAR);
		return new IPath[] { source, prefix };
	}

	private static IPath[] getSourceAnnotation(
		IPluginBase pluginBase,
		IPath rootPath,
		String name) {
		IPath[] annot = new IPath[2];
		int dot = name.lastIndexOf('.');
		if (dot != -1) {
			String zipName = name.substring(0, dot) + "src.zip";
			// test the sibling location
			if (exists(pluginBase, rootPath, zipName)) {
				annot[0] = rootPath.append(zipName);
			} else {
				// must look up source locations
				annot[0] = findSourceZip(pluginBase, zipName);
			}
		}
		return annot;
	}

	private static IPath findSourceZip(
		IPluginBase pluginBase,
		String zipName) {
		SourceLocationManager manager =
			PDECore.getDefault().getSourceLocationManager();
		return manager.findVariableRelativePath(pluginBase, new Path(zipName));
	}

	private static boolean exists(
		IPluginBase plugin,
		IPath rootPath,
		String zipName) {
		IResource resource = plugin.getModel().getUnderlyingResource();
		if (resource != null) {
			IProject project = resource.getProject();
			IFile file = project.getFile(zipName);
			return file.exists();
		} else {
			rootPath = getAbsolutePath(rootPath);
			File file = rootPath.append(zipName).toFile();
			return file.exists();
		}
	}

	private static IPath getAbsolutePath(IPath path) {
		String firstSegment = path.segment(0);
		if (firstSegment.equals("ECLIPSE_HOME")) {
			IPath root = JavaCore.getClasspathVariable(firstSegment);
			if (root != null)
				return root.append(path.removeFirstSegments(1));
		}
		return path;
	}

	public IClasspathEntry[] getSourceClasspathEntries(IPluginModel model) {
		Vector result = new Vector();

		addFoldersToClasspathEntries(model, result);
		IClasspathEntry[] finalEntries = new IClasspathEntry[result.size()];
		result.copyInto(finalEntries);
		return finalEntries;
	}

	public static boolean isAlreadyPresent(
		IClasspathEntry[] oldEntries,
		IClasspathEntry entry) {
		for (int i = 0; i < oldEntries.length; i++) {
			IClasspathEntry oldEntry = oldEntries[i];
			if (oldEntry.getContentKind() == entry.getContentKind()
				&& oldEntry.getEntryKind() == entry.getEntryKind()
				&& oldEntry.getPath().equals(entry.getPath())) {
				return true;
			}
		}
		return false;
	}

	public static void addImplicitLibraries(
		Vector result,
		boolean relative,
		boolean addRuntime) {
		String bootId = "org.eclipse.core.boot";
		String runtimeId = "org.eclipse.core.runtime";
		IPlugin bootPlugin = PDECore.getDefault().findPlugin(bootId);

		if (addRuntime) {
			IPlugin runtimePlugin = PDECore.getDefault().findPlugin(runtimeId);
			if (runtimePlugin != null) {
				addToClasspathEntries(
					new PluginEntry(runtimePlugin),
					relative,
					result);
			}
		}
		if (bootPlugin != null) {
			addToClasspathEntries(new PluginEntry(bootPlugin), relative, result);
		}
	}

	private static String expandLibraryName(String source) {
		if (source == null || source.length() == 0)
			return "";
		if (source.charAt(0) != '$')
			return source;
		IPath path = new Path(source);
		String firstSegment = path.segment(0);
		if (firstSegment.charAt(firstSegment.length() - 1) != '$')
			return source;
		String variable = firstSegment.substring(1, firstSegment.length() - 1);
		variable = variable.toLowerCase();
		if (variable.equals("ws")) {
			variable = TargetPlatform.getWS();
			if (variable != null)
				variable = "ws" + File.separator + variable;
		} else if (variable.equals("os")) {
			variable = TargetPlatform.getOS();
			if (variable != null)
				variable = "os" + File.separator + variable;
		} else
			variable = null;
		if (variable != null) {
			path = path.removeFirstSegments(1);
			return variable + path.SEPARATOR + path.toString();
		}
		return source;
	}

}