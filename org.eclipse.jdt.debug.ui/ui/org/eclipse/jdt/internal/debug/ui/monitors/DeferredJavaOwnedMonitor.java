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
package org.eclipse.jdt.internal.debug.ui.monitors;


/**
 * Workbench adapter for a owned monitor.
 */
public class DeferredJavaOwnedMonitor extends DeferredMonitorElement {
    
    /* (non-Javadoc)
     * @see org.eclipse.ui.model.IWorkbenchAdapter#getChildren(java.lang.Object)
     */
    public Object[] getChildren(Object parent) {
        return ((JavaOwnedMonitor)parent).getWaitingThreads();
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.model.IWorkbenchAdapter#getParent(java.lang.Object)
     */
    public Object getParent(Object element) {
		JavaWaitingThread parent= ((JavaOwnedMonitor)element).getParent();
		if (parent.getParent() == null) {
			return parent.getThread();
		}
		return parent;
    }

}
