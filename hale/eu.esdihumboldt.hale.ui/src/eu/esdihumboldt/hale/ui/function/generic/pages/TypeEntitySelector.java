/*
 * HUMBOLDT: A Framework for Data Harmonisation and Service Integration.
 * EU Integrated Project #030962                 01.10.2006 - 30.09.2010
 * 
 * For more information on the project, please refer to the this web site:
 * http://www.esdi-humboldt.eu
 * 
 * LICENSE: For information on the license under which this program is 
 * available, please refer to http:/www.esdi-humboldt.eu/license.html#core
 * (c) the HUMBOLDT Consortium, 2007 to 2011.
 */

package eu.esdihumboldt.hale.ui.function.generic.pages;

import java.util.Set;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import eu.esdihumboldt.hale.align.extension.function.AbstractParameter;
import eu.esdihumboldt.hale.align.model.Entity;
import eu.esdihumboldt.hale.align.model.EntityDefinition;
import eu.esdihumboldt.hale.align.model.Type;
import eu.esdihumboldt.hale.align.model.impl.DefaultType;
import eu.esdihumboldt.hale.align.model.impl.TypeEntityDefinition;
import eu.esdihumboldt.hale.ui.service.schema.SchemaSpaceID;

/**
 * TODO Type description
 * @author sitemple
 */
public class TypeEntitySelector extends EntitySelector {

	/**
	 * @param ssid
	 * @param candidates
	 * @param field
	 * @param parent
	 */
	public TypeEntitySelector(SchemaSpaceID ssid,
			Set<EntityDefinition> candidates, AbstractParameter field,
			Composite parent) {
		super(ssid, candidates, field, parent);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @see EntitySelector#createEntityDialog(Shell, SchemaSpaceID, AbstractParameter)
	 */
	@Override
	protected EntityDialog createEntityDialog(Shell parentShell,
			SchemaSpaceID ssid, AbstractParameter field) {
		String title;
		switch (ssid) {
		case SOURCE:
			title = "Select source type";
			break;
		case TARGET:
		default:
			title = "Select target type";
			break;
		}
		return new TypeEntityDialog(parentShell, ssid, title);
	}

	/**
	 * @see EntitySelector#createEntity(EntityDefinition)
	 */
	@Override
	protected Entity createEntity(EntityDefinition element) {
		if (element instanceof TypeEntityDefinition) {
			Type type = new DefaultType((TypeEntityDefinition) element);
			//TODO configure entity?
			return type;
		}
		
		throw new IllegalArgumentException("Entity must be a type");
	}

}
