package org.eclipse.jdt.internal.debug.ui.snippeteditor;
 
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventListener;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.ILauncher;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIEventFilter;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.internal.debug.ui.launcher.JavaApplicationLauncherDelegate;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.ProjectSourceLocator;
import org.eclipse.jdt.launching.VMRunnerConfiguration;
import org.eclipse.jdt.launching.VMRunnerResult;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

public class ScrapbookLauncherDelegate extends JavaApplicationLauncherDelegate implements IDebugEventListener {
	
	IJavaLineBreakpoint fMagicBreakpoint;
	DebugException fDebugException;
	
	HashMap fScrapbookToVMs = new HashMap(10);
	HashMap fVMsToBreakpoints = new HashMap(10);
	HashMap fVMsToScrapbooks = new HashMap(10);
	HashMap fVMsToFilters = new HashMap(10);
	
	public ScrapbookLauncherDelegate() {
		DebugPlugin.getDefault().addDebugEventListener(this);
	}
	
	public static ILauncher getLauncher() {
		ILauncher[] launchers = DebugPlugin.getDefault().getLaunchManager().getLaunchers();
		ILauncher me= null;
		for (int i = 0; i < launchers.length; i++) {
			if (launchers[i].getIdentifier().equals("org.eclipse.jdt.debug.ui.launcher.ScrapbookLauncherDelegate")) { //$NON-NLS-1$
				me = launchers[i];
				break; 
			}
		}
		return me;
	}
	
	public static ScrapbookLauncherDelegate getDefault() {
		return (ScrapbookLauncherDelegate)getLauncher().getDelegate();
	}
	
	/**
	 *	@see JavaApplicationLauncher#launchElement
	 */
	protected boolean launchElement(Object runnable, String mode, ILauncher launcher) {
		
		if (!(runnable instanceof IFile)) {
			showNoPageDialog();
			return false;
		}

		IFile page = (IFile)runnable;
		
		if (!page.getFileExtension().equals("jpage")) { //$NON-NLS-1$
			showNoPageDialog();
			return false;
		}
		
		if (fScrapbookToVMs.get(page) != null) {
			//already launched
			return false;
		}
		
		IJavaProject javaProject= JavaCore.create(page.getProject());
			
		URL pluginInstallURL= JDIDebugUIPlugin.getDefault().getDescriptor().getInstallURL();
		URL jarURL = null;
		try {
			jarURL = new URL(pluginInstallURL, "snippetsupport.jar"); //$NON-NLS-1$
			jarURL = Platform.asLocalURL(jarURL);
		} catch (MalformedURLException e) {
			JDIDebugUIPlugin.log(e);
			return false;
		} catch (IOException e) {
			JDIDebugUIPlugin.log(e);
			return false;
		}
		
		String[] classPath = new String[] {jarURL.getFile()};
		
		return doLaunch(javaProject, mode, page, classPath, launcher);
	}

	private boolean doLaunch(IJavaProject p, String mode, IFile page, String[] classPath, ILauncher launcherProxy) {
		try {
			IVMRunner runner= getVMRunner(p, mode);
			if (runner == null) {
				return false;
			}
						
			ISourceLocator sl= new ProjectSourceLocator(p);
			IPath outputLocation =	p.getProject().getPluginWorkingLocation(JDIDebugUIPlugin.getDefault().getDescriptor());
			File f = outputLocation.toFile();
			URL u = null;
			try {
				u = f.toURL();
			} catch (MalformedURLException e) {
				return false;
			}
			String[] defaultClasspath = JavaRuntime.computeDefaultRuntimeClassPath(p);
			String[] urls = new String[defaultClasspath.length + 1];
			urls[0] = u.toExternalForm();
			for (int i = 0; i < defaultClasspath.length; i++) {
				f = new File(defaultClasspath[i]);
				try {
					urls[i + 1] = f.toURL().toExternalForm();
				} catch (MalformedURLException e) {
					return false;
				}
			}
			
			VMRunnerConfiguration config= new VMRunnerConfiguration("org.eclipse.jdt.internal.debug.ui.snippeteditor.ScrapbookMain", classPath); //$NON-NLS-1$
			config.setProgramArguments(urls);
			
			VMRunnerResult result= runner.run(config);
			if (result != null) {
				IDebugTarget dt = result.getDebugTarget();
				IBreakpoint magicBreakpoint = createMagicBreakpoint(getMainType(p));
				fScrapbookToVMs.put(page, dt);
				fVMsToScrapbooks.put(dt, page);
				fVMsToBreakpoints.put(dt, magicBreakpoint);
				dt.breakpointAdded(magicBreakpoint);
				Launch newLaunch= new Launch(launcherProxy, mode, page, sl,result.getProcesses(), dt);
				IDebugUIEventFilter filter = new ScrapbookEventFilter(newLaunch);
				fVMsToFilters.put(dt, filter);
				DebugUITools.addEventFilter(filter);
				DebugPlugin.getDefault().getLaunchManager().registerLaunch(newLaunch);
				return true;
			}
		} catch (CoreException e) {
			JDIDebugUIPlugin.log(e);
			ErrorDialog.openError(JDIDebugUIPlugin.getActiveWorkbenchShell(), SnippetMessages.getString("ScrapbookLauncher.error.title"), SnippetMessages.getString("ScrapbookLauncher.error.exception"), e.getStatus()); //$NON-NLS-2$ //$NON-NLS-1$
		}
		return false;
	}

	/**
	 * Creates an "invisible" breakpoint by using a run-to-line
	 * breakpoint without a hit count. 
	 */
	IBreakpoint createMagicBreakpoint(IType type) {
		try {
			fMagicBreakpoint= JDIDebugModel.createRunToLineBreakpoint(type, 49, -1, -1);
			fMagicBreakpoint.setHitCount(0);
			return fMagicBreakpoint;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}

	IType getMainType(IJavaProject jp) {	
		try {
			return jp.getPackageFragmentRoot(jp.getUnderlyingResource()).
				getPackageFragment("org.eclipse.jdt.internal.debug.ui.snippeteditor"). //$NON-NLS-1$
				getClassFile("ScrapbookMain.class").getType(); //$NON-NLS-1$
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return null;
	}
		
	/**
	 * @see JavaApplicationLauncherDelegate#getLaunchableElements(IStructuredSelection, String)
	 */
	public Object[] getLaunchableElements(IStructuredSelection selection, String mode) {
		if (mode.equals(ILaunchManager.DEBUG_MODE)) {
			if (!selection.isEmpty()) {
				ArrayList list = new ArrayList(1);
				Iterator i = selection.iterator();
				while (i.hasNext()) {
					Object o = i.next();
					if (o instanceof IFile && ((IFile)o).getFileExtension().equals("jpage")) { //$NON-NLS-1$
						list.add(o);
					}
				}
				return list.toArray();
			}
		} 
		return new Object[0];
	}

	/**
	 * @see IDebugEventListener#handleDebugEvent(DebugEvent)
	 */
	public void handleDebugEvent(DebugEvent event) {
		if (event.getSource() instanceof IDebugTarget && event.getKind() == DebugEvent.TERMINATE) {
			cleanup((IDebugTarget)event.getSource());
		}
	}
	
	public IDebugTarget getDebugTarget(IFile page) {
		return (IDebugTarget)fScrapbookToVMs.get(page);
	}
	
	public IBreakpoint getMagicBreakpoint(IDebugTarget target) {
		return (IBreakpoint)fVMsToBreakpoints.get(target);
	}
	
	protected void showNoPageDialog() {
		String title= SnippetMessages.getString("ScrapbookLauncher.error.title"); //$NON-NLS-1$
		String msg= SnippetMessages.getString("ScrapbookLauncher.error.pagenotfound"); //$NON-NLS-1$
		MessageDialog.openError(JDIDebugUIPlugin.getActiveWorkbenchShell(),title, msg);
	}
	
	protected void cleanup(IDebugTarget target) {
		Object page = fVMsToScrapbooks.get(target);
		if (page != null) {
			fVMsToScrapbooks.remove(target);
			fScrapbookToVMs.remove(page);
			fVMsToBreakpoints.remove(target);
			IDebugUIEventFilter filter = (IDebugUIEventFilter)fVMsToFilters.remove(target);
			DebugUITools.removeEventFilter(filter);
		}
	}
}