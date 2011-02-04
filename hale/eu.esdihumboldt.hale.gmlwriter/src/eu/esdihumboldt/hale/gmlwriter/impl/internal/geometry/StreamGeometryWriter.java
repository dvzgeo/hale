/*
 * HUMBOLDT: A Framework for Data Harmonisation and Service Integration.
 * EU Integrated Project #030962                 01.10.2006 - 30.09.2010
 * 
 * For more information on the project, please refer to the this web site:
 * http://www.esdi-humboldt.eu
 * 
 * LICENSE: For information on the license under which this program is 
 * available, please refer to http:/www.esdi-humboldt.eu/license.html#core
 * (c) the HUMBOLDT Consortium, 2007 to 2010.
 */

package eu.esdihumboldt.hale.gmlwriter.impl.internal.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.geotools.feature.NameImpl;
import org.opengis.feature.type.Name;

import com.vividsolutions.jts.geom.Geometry;

import de.cs3d.util.logging.ALogger;
import de.cs3d.util.logging.ALoggerFactory;

import eu.esdihumboldt.hale.gmlwriter.impl.internal.GmlWriterUtil;
import eu.esdihumboldt.hale.gmlwriter.impl.internal.StreamGmlWriter;
import eu.esdihumboldt.hale.gmlwriter.impl.internal.geometry.GeometryConverterRegistry.ConversionLadder;
import eu.esdihumboldt.hale.gmlwriter.impl.internal.geometry.writers.MultiLineStringWriter;
import eu.esdihumboldt.hale.gmlwriter.impl.internal.geometry.writers.PointWriter;
import eu.esdihumboldt.hale.gmlwriter.impl.internal.geometry.writers.PolygonWriter;
import eu.esdihumboldt.hale.schemaprovider.model.AttributeDefinition;
import eu.esdihumboldt.hale.schemaprovider.model.TypeDefinition;

/**
 * Write geometries for a GML document
 *
 * @author Simon Templer
 * @partner 01 / Fraunhofer Institute for Computer Graphics Research
 * @version $Id$ 
 */
public class StreamGeometryWriter {
	
	/**
	 * Path candidate
	 */
	private static class PathCandidate {

		private final TypeDefinition type;
		private final DefinitionPath path;
		private final HashSet<TypeDefinition> checkedTypes;

		/**
		 * Constructor
		 * 
		 * @param type the associated type
		 * @param path the definition path
		 * @param checkedTypes the type definitions that have already been checked
		 *   (to prevent cycles)
		 */
		public PathCandidate(TypeDefinition type,
				DefinitionPath path, HashSet<TypeDefinition> checkedTypes) {
			this.type = type;
			this.path = path;
			this.checkedTypes = checkedTypes;
		}

		/**
		 * @return the attributeType
		 */
		public TypeDefinition getType() {
			return type;
		}

		/**
		 * @return the definitionPath
		 */
		public DefinitionPath getPath() {
			return path;
		}

		/**
		 * @return the handledTypes
		 */
		public HashSet<TypeDefinition> getCheckedTypes() {
			return checkedTypes;
		}

	}

	private static final ALogger log = ALoggerFactory.getLogger(StreamGeometryWriter.class);
	
	/**
	 * Get a geometry writer instance with a default configuration
	 * 
	 * @param gmlNs the GML namespace 
	 * 
	 * @return the geometry writer
	 */
	public static StreamGeometryWriter getDefaultInstance(String gmlNs) {
		StreamGeometryWriter sgm = new StreamGeometryWriter(gmlNs);
		
		//TODO configure
		sgm.registerGeometryWriter(new MultiLineStringWriter());
		sgm.registerGeometryWriter(new PointWriter());
		sgm.registerGeometryWriter(new PolygonWriter());
		
		return sgm;
	}
	
	/**
	 * The GML namespace
	 */
	private final String gmlNs;
	
	/**
	 * Geometry types mapped to compatible writers
	 */
	private final Map<Class<? extends Geometry>, Set<GeometryWriter<?>>> geometryWriters =
		new HashMap<Class<? extends Geometry>, Set<GeometryWriter<?>>>();
	
	/**
	 * Types mapped to geometry types mapped to matched definition paths
	 */
	private final Map<TypeDefinition, Map<Class<? extends Geometry>, DefinitionPath>> storedPaths = 
		new HashMap<TypeDefinition, Map<Class<? extends Geometry>,DefinitionPath>>(); 

	/**
	 * Constructor
	 * 
	 * @param gmlNs the GML namespace
	 */
	public StreamGeometryWriter(String gmlNs) {
		super();
		
		this.gmlNs = gmlNs;
	}
	
	/**
	 * Register a geometry writer
	 *  
	 * @param writer the geometry writer
	 */
	public void registerGeometryWriter(GeometryWriter<?> writer) {
		Class<? extends Geometry> geomType = writer.getGeometryType();
		Set<GeometryWriter<?>> writers = geometryWriters.get(geomType);
		if (writers == null) {
			writers = new HashSet<GeometryWriter<?>>();
			geometryWriters.put(geomType, writers);
		}
		
		writers.add(writer);
	}

	/**
	 * Write a geometry to a stream for a GML document
	 * 
	 * @param writer the XML stream writer
	 * @param geometry the geometry
	 * @param attributeType the attribute type
	 * @throws XMLStreamException if any error occurs writing the geometry
	 */
	public void write(XMLStreamWriter writer, Geometry geometry,
			TypeDefinition attributeType) throws XMLStreamException {
		Class<? extends Geometry> geomType = geometry.getClass();
		
		// remember if we already found a solution to this problem
		DefinitionPath path = restoreCandidate(attributeType, geomType);
		
		if (path == null) {
			// find candidates
			List<DefinitionPath> candidates = findCandidates(attributeType, geomType);
			
			// if no candidate found, try with compatible geometries
			Class<? extends Geometry> originalType = geomType;
			Geometry originalGeometry = geometry;
			ConversionLadder ladder = GeometryConverterRegistry.getInstance().createLadder(geometry);
			while (candidates.isEmpty() && ladder.hasNext()) {
				geometry = ladder.next();
				geomType = geometry.getClass();
				
				log.info("Possible structure for writing " + originalType.getSimpleName() + 
						" not found, trying " + geomType.getSimpleName() + " instead");
				
				DefinitionPath candPath = restoreCandidate(attributeType, geomType);
				if (candPath != null) {
					// use stored candidate
					candidates = Collections.singletonList(candPath);
				}
				else {
					candidates = findCandidates(attributeType, geomType);
				}
			}
			
			for (DefinitionPath candidate : candidates) {
				log.info("Geometry structure match: " + geomType.getSimpleName() + " - " + candidate);
			}
			
			if (candidates.isEmpty()) {
				log.error("No geometry structure match for " + 
						originalType.getSimpleName() + " found, writing WKT " +
						"representation instead");
				
				writer.writeCharacters(originalGeometry.toText());
				return;
			}
			
			// determine preferred candidate
			//XXX for now: first one
			path = candidates.get(0);
			
			// remember for later
			storeCandidate(attributeType, geomType, path);
		}
		
		// write geometry
		writeGeometry(writer, geometry, path);
	}

	/**
	 * Write the geometry using the given path
	 * 
	 * @param writer the XML stream writer
	 * @param geometry the geometry
	 * @param path the definition path to use
	 * @throws XMLStreamException if writing the geometry fails
	 */
	@SuppressWarnings("unchecked")
	private void writeGeometry(XMLStreamWriter writer, Geometry geometry,
			DefinitionPath path) throws XMLStreamException {
		GeometryWriter geomWriter = path.getGeometryWriter();
		
		Name name = GmlWriterUtil.getElementName(path.getLastType()); //XXX the element name used may be wrong, is this an issue?
		
		if (path.isEmpty()) {
			// directly write geometry
			geomWriter.write(writer, geometry, path.getLastType(), name, gmlNs); 
		}
		else {
			for (PathElement step : path.getSteps()) {
				// start elements
				name = step.getName();
				writer.writeStartElement(name.getNamespaceURI(), name.getLocalPart());
				// write eventual required ID
				StreamGmlWriter.writeRequiredID(writer, step.getType(), null, false);
			}
			
			// write geometry
			geomWriter.write(writer, geometry, path.getLastType(), name, gmlNs);
			
			for (int i = 0; i < path.getSteps().size(); i++) {
				// end elements
				writer.writeEndElement();
			}
		}
	}

	/**
	 * Store the candidate for later use
	 * 
	 * @param type the attribute type definition
	 * @param geomType the geometry type
	 * @param path the definition path
	 */
	private void storeCandidate(TypeDefinition type,
			Class<? extends Geometry> geomType, DefinitionPath path) {
		Map<Class<? extends Geometry>, DefinitionPath> paths = storedPaths.get(type);
		if (paths == null) {
			paths = new HashMap<Class<? extends Geometry>, DefinitionPath>();
			storedPaths.put(type, paths);
		}
		paths.put(geomType, path);
	}
	
	/**
	 * Restore the candidate matching the given types
	 * 
	 * @param type the attribute type definition
	 * @param geomType the geometry type
	 * 
	 * @return a previously found path or <code>null</code> 
	 */
	private DefinitionPath restoreCandidate(TypeDefinition type,
			Class<? extends Geometry> geomType) {
		Map<Class<? extends Geometry>, DefinitionPath> paths = storedPaths.get(type);
		if (paths != null) {
			return paths.get(geomType);
		}
		return null;
	}

	/**
	 * Find candidates for a possible path to use for writing the geometry
	 * 
	 * @param attributeType the start attribute type
	 * @param geomType the geometry type
	 * 
	 * @return the path candidates
	 */
	private List<DefinitionPath> findCandidates(TypeDefinition attributeType,
			Class<? extends Geometry> geomType) {
		Set<GeometryWriter<?>> writers = geometryWriters.get(geomType);
		if (writers == null || writers.isEmpty()) {
			// if no writer is present, we can cancel right here
			return new ArrayList<DefinitionPath>();
		}
		
		Queue<PathCandidate> candidates = new LinkedList<PathCandidate>();
		PathCandidate base = new PathCandidate(attributeType, 
				new DefinitionPath(attributeType),
				new HashSet<TypeDefinition>());
		candidates.add(base);
		
		while (!candidates.isEmpty()) {
			PathCandidate candidate = candidates.poll();
			TypeDefinition type = candidate.getType();
			DefinitionPath basePath = candidate.getPath();
			HashSet<TypeDefinition> checkedTypes = candidate.getCheckedTypes();
			
			if (checkedTypes.contains(type)) {
				continue; // prevent cycles
			}
			else {
				checkedTypes.add(type);
			}
			
			// check if there is a direct match
			DefinitionPath path = matchPath(type, geomType, basePath);
			if (path != null) {
				return Collections.singletonList(path); // return instantly
				//XXX currently always only one path is returned - this might change if we allow matchPath to yield multiple results
			}
			
			if (!type.isAbstract()) { // only allow stepping down properties if the type is not abstract
				// step down properties
				Iterable<AttributeDefinition> properties = (basePath.isEmpty() || basePath.getLastElement().isProperty())?(type.getAttributes()):(type.getDeclaredAttributes());
				for (AttributeDefinition att : properties) {
					if (att.isElement()) { // only descend into elements
						candidates.add(new PathCandidate(att.getAttributeType(), 
								new DefinitionPath(basePath).addProperty(att), 
								new HashSet<TypeDefinition>(checkedTypes)));
					}
				}
			}
			
			// step down sub-types
			for (TypeDefinition subtype : type.getSubTypes()) {
				candidates.add(new PathCandidate(subtype,
						new DefinitionPath(basePath).addSubType(subtype),
						new HashSet<TypeDefinition>(checkedTypes)));
			}
		}
		
		return new ArrayList<DefinitionPath>();
	}

	/**
	 * Determines if a type definition is compatible to a geometry type
	 *  
	 * @param type the type definition
	 * @param geomType the geometry type
	 * @param path the current definition path
	 * 
	 * @return the (eventually updated) definition path if a match is found,
	 * otherwise <code>null</code>

	 */
	protected DefinitionPath matchPath(TypeDefinition type, 
			Class<? extends Geometry> geomType, DefinitionPath path) {
		
		// check compatibility list
		Set<GeometryWriter<?>> writers = geometryWriters.get(geomType);
		if (writers != null) {
			for (GeometryWriter<?> writer : writers) {
				boolean compatible = false;
				Set<Name> names = writer.getCompatibleTypes();
				if (names != null) {
					if (names.contains(type.getName())) {
						// check type name
						compatible = true;
					}
					
					if (!compatible && type.getName().getNamespaceURI().equals(gmlNs)) {
						// check GML type name
						compatible = names.contains(new NameImpl(null, type.getName().getLocalPart()));
					}
					
					if (compatible) {
						// check structure / match writer
						DefinitionPath candidate = writer.match(type, path, gmlNs);
						if (candidate != null) {
							// set appropriate writer for path and return it
							candidate.setGeometryWriter(writer);
							return candidate;
						}
					}
				}
			}
		}
		
		// fall back to binding test
		// check for equality because we don't want a match for the property types
		boolean compatible = type.getType(null).getBinding().equals(geomType);
		
		if (compatible) {
			// check structure / match writers
			if (writers != null) {
				for (GeometryWriter<?> writer : writers) {
					DefinitionPath candidate = writer.match(type, path, gmlNs);
					if (candidate != null) {
						// set appropriate writer for path and return it
						candidate.setGeometryWriter(writer);
						return candidate;
					}
				}
			}
		}
		
		return null;
	}

}
