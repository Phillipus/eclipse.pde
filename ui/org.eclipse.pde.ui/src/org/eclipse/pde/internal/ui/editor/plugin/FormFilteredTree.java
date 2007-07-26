/*******************************************************************************
 * Copyright (c) 2006, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.editor.plugin;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.pde.internal.ui.editor.FormEntryAdapter;
import org.eclipse.pde.internal.ui.editor.FormLayoutFactory;
import org.eclipse.pde.internal.ui.editor.IContextPart;
import org.eclipse.pde.internal.ui.parts.FormEntry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormToolkit;

public class FormFilteredTree extends FilteredTree {
	
	private FormToolkit toolkit;
	
	private FormEntry fEntryFilter;
	
	public FormFilteredTree(Composite parent, int treeStyle,
			PatternFilter filter) {
		super(parent, treeStyle, filter);
	}
	
	protected void createControl(Composite parent, int treeStyle) {
		toolkit = new FormToolkit(parent.getDisplay());
		GridLayout layout = FormLayoutFactory.createClearGridLayout(false, 1);
		// Space between filter text field and tree viewer
		layout.verticalSpacing = 3;
		setLayout(layout);
		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        if (showFilterControls){
        	filterComposite = new Composite(this, SWT.NONE);
        	GridLayout filterLayout = FormLayoutFactory.createClearGridLayout(false, 2);
        	filterLayout.horizontalSpacing = 5;
        	filterComposite.setLayout(filterLayout);
            filterComposite.setFont(parent.getFont());
        	createFilterControls(filterComposite);
        	filterComposite.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING,
					true, false));
        }
        
        treeComposite = new Composite(this, SWT.NONE);
		treeComposite.setLayout(FormLayoutFactory.createClearGridLayout(false, 1));
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
        treeComposite.setLayoutData(data);
        createTreeControl(treeComposite, treeStyle); 
	}

	/* (non-Javadoc)
	 * @see org.eclipse.swt.widgets.Widget#dispose()
	 */
	public void dispose() {
		if (toolkit != null) {
			toolkit.dispose();
			toolkit = null;
		}
		super.dispose();
	}

	protected Text doCreateFilterText(Composite parent) {
		int style = SWT.SINGLE | toolkit.getBorderStyle();
		fEntryFilter = new FormEntry(parent, toolkit, null, style);
		// Needed otherwise borders are missing on Windows Classic Theme
		toolkit.paintBordersFor(parent);
		setBackground(toolkit.getColors().getBackground());
		return fEntryFilter.getText();
	}

	protected TreeViewer doCreateTreeViewer(Composite parent, int style) {
		TreeViewer viewer = super.doCreateTreeViewer(parent, toolkit.getBorderStyle() | style);
		toolkit.paintBordersFor(viewer.getTree().getParent());
		return viewer;
	}
	
	/**
	 * @param part
	 */
	public void createUIListenerEntryFilter(IContextPart part) {
		// Required to enable Ctrl-V initiated paste operation on first focus
		// See Bug # 157973
		fEntryFilter.setFormEntryListener(new FormEntryAdapter(part) {
			// Override all callback methods except focusGained
			// See Bug # 184085
			public void browseButtonSelected(FormEntry entry) {
				// NO-OP
			}
			public void linkActivated(HyperlinkEvent e) {
				// NO-OP
			}
			public void linkEntered(HyperlinkEvent e) {
				// NO-OP
			}
			public void linkExited(HyperlinkEvent e) {
				// NO-OP
			}
			public void selectionChanged(FormEntry entry) {
				// NO-OP
			}
			public void textDirty(FormEntry entry) {
				// NO-OP
			}
			public void textValueChanged(FormEntry entry) {
				// NO-OP
			}
		});
	}
	
	/**
	 * @return a boolean indicating whether the tree is filtered or not.
	 */
	public boolean isFiltered() {
		String filterText = getFilterControl().getText();
		boolean filtered = (filterText != null && filterText.length() > 0 && !filterText.equals(getInitialText()));
		return filtered;
	}
}
