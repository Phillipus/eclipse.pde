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
package org.eclipse.pde.internal.core.schema;

import java.io.*;
import java.net.*;

import org.eclipse.pde.core.*;
import org.eclipse.pde.internal.core.ischema.*;

public class EditableSchema extends Schema implements IEditable {

	public EditableSchema(ISchemaDescriptor schemaDescriptor, URL url, boolean abbreviated) {
		super(schemaDescriptor, url, abbreviated);
	}
	
	public EditableSchema(String pluginId, String pointId, String name, boolean abbreviated) {
		super(pluginId, pointId, name, abbreviated);
	}
	

	private boolean dirty;

	public void fireModelChanged(IModelChangedEvent event) {
		if (isNotificationEnabled())
			dirty = true;
		super.fireModelChanged(event);
	}

	public boolean isDirty() {
		return dirty;
	}

	public boolean isEditable() {
		return true;
	}

	public void save(PrintWriter writer) {
		this.write("", writer); //$NON-NLS-1$
		dirty = false;
	}

	public void setDirty(boolean newDirty) {
		dirty = newDirty;
	}
}
