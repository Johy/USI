/*
 * Copyright or © or Copr. école des mines d'Alès (2014) 
 * 
 * <nicolas.fiorini@mines-ales.fr> / <contact@creatox.com>
 * 
 * This software is a computer program whose purpose is to semantically
 * index entities of any type, given an annotated neighbourhood.
 * 
 * This software is governed by the CeCILL  license under French law and
 * abiding by the rules of distribution of free software.  You can  use, 
 * modify and/ or redistribute the software under the terms of the CeCILL
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info". 
 * 
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability. 
 * 
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or 
 * data to be ensured and,  more generally, to use and operate it in the 
 * same conditions as regards security. 
 * 
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL license and that you accept its terms.
 */

package managers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.RDFS;
import slib.indexer.IndexHash;
import slib.indexer.mesh.Indexer_MESH_XML;
import slib.sglib.algo.graph.validator.dag.ValidatorDAG;
import slib.sglib.io.conf.GDataConf;
import slib.sglib.io.loader.GraphLoaderGeneric;
import slib.sglib.io.util.GFormat;
import slib.sglib.model.graph.G;
import slib.sglib.model.graph.elements.E;
import slib.sglib.model.graph.utils.Direction;
import slib.sglib.model.impl.graph.memory.GraphMemory;
import slib.sglib.model.impl.repo.URIFactoryMemory;
import slib.sml.sm.core.engine.SM_Engine;
import slib.utils.ex.SLIB_Ex_Critic;
import slib.utils.ex.SLIB_Exception;
import tools.MeasuresConf;

/**
 *
 * @author Nicolas Fiorini <nicolas.fiorini@mines-ales.fr> / <contact@creatox.com>
 */
public class EngineOverlay {

    SM_Engine sme;
    HashMap<String, URI> labels = new HashMap();
    IndexHash ih;

    /**
     *
     * @param l
     * @return
     */
    public URI getFromLabel(String l) {
        return labels.get(l);
    }

    /**
     *
     * @return
     */
    public SM_Engine getEngine() {
        return sme;
    }

    /**
     *
     * @param u
     * @return
     */
    public String getFromURI(URI u) {
        return ih.valuesOf(u).getPreferredDescription();
    }

    /**
     *
     * @param ontologyFilePath
     * @throws SLIB_Exception
     */
    public EngineOverlay(String ontologyFilePath) throws SLIB_Exception {
        URIFactoryMemory factory = URIFactoryMemory.getSingleton();
        String b = "http://usi";
        URI uri = factory.createURI(b);
        G ontologyGraph = new GraphMemory(uri);
        GDataConf dataMeshXML = new GDataConf(GFormat.MESH_XML, ontologyFilePath); // the DTD must be located in the same directory
        dataMeshXML.addParameter("prefix", b + "/");
        try {
            GraphLoaderGeneric.populate(dataMeshXML, ontologyGraph);
        } catch (SLIB_Exception e) {
            e.printStackTrace();
        }
        removeMeshCycles(ontologyGraph);
        sme = new SM_Engine(ontologyGraph);
        Indexer_MESH_XML indexer = new Indexer_MESH_XML();
        ih = indexer.buildIndex(factory, ontologyFilePath, b + "/");
        System.out.println("Index of URIs done.");
        Set<URI> uris = sme.getClasses();
        for (URI u : uris) {
            if (u != null && ih.valuesOf(u) != null && ih.valuesOf(u).getPreferredDescription() != null) {
                labels.put(ih.valuesOf(u).getPreferredDescription().toLowerCase(), u);
            }
        }
        System.out.println("URIs mapped with preferred names.");
    }

    /**
     *
     * @param set1
     * @param set2
     * @return
     */
    public double calculateGroupwise(Set<URI> set1, Set<URI> set2) {
        try {
            return sme.computeGroupwiseAddOnSim(MeasuresConf.getAgreg(), MeasuresConf.getMeasure(), set1, set2);
        } catch (SLIB_Ex_Critic ex) {
            return 0.;
        }
    }

    /**
     *
     * @param u1
     * @param u2
     * @return
     */
    public double calculatePairwise(URI u1, URI u2) {
        try {
            return sme.computePairwiseSim(MeasuresConf.getMeasure(), u1, u2);
        } catch (SLIB_Ex_Critic ex) {
            return 0.;
        }
    }

    /*
     * We remove the cycles of the graph in order to obtain 
     * a rooted directed acyclic graph (DAG) and therefore be able to 
     * use most of semantic similarity measures.
     * see http://semantic-measures-library.org/sml/index.php?q=doc&page=mesh
     */
    private void removeMeshCycles(G meshGraph) throws SLIB_Ex_Critic {
        URIFactoryMemory factory = URIFactoryMemory.getSingleton();

        // We remove the edges creating cycles
        URI ethicsURI = factory.createURI(meshGraph.getURI().stringValue() + "/D004989");
        URI moralsURI = factory.createURI(meshGraph.getURI().stringValue() + "/D009014");

        // We retrieve the direct subsumers of the concept (D009014)
        Set<E> moralsEdges = meshGraph.getE(RDFS.SUBCLASSOF, moralsURI, Direction.OUT);
        for (E e : moralsEdges) {

            System.out.println("\t" + e);
            if (e.getTarget().equals(ethicsURI)) {
                System.out.println("\t*** Removing edge " + e);
                meshGraph.removeE(e);
            }
        }

        ValidatorDAG validatorDAG = new ValidatorDAG();
        boolean isDAG = validatorDAG.containsTaxonomicDag(meshGraph);

        System.out.println("MeSH Graph is a DAG: " + isDAG);

        // We remove the edges creating cycles
        // see http://semantic-measures-library.org/sml/index.php?q=doc&page=mesh

        URI hydroxybutyratesURI = factory.createURI(meshGraph.getURI().stringValue() + "/D006885");
        URI hydroxybutyricAcidURI = factory.createURI(meshGraph.getURI().stringValue() + "/D020155");

        // We retrieve the direct subsumers of the concept (D009014)
        Set<E> hydroxybutyricAcidEdges = meshGraph.getE(RDFS.SUBCLASSOF, hydroxybutyricAcidURI, Direction.OUT);
        for (E e : hydroxybutyricAcidEdges) {

            System.out.println("\t" + e);
            if (e.getTarget().equals(hydroxybutyratesURI)) {
                System.out.println("\t*** Removing edge " + e);
                meshGraph.removeE(e);
            }
        }
    }

    /**
     *
     * @param startingConceptURI
     * @param similarityThreshold
     * @return
     * @throws SLIB_Ex_Critic
     */
    public Set<URI> findNeighboringConcepts(URI startingConceptURI, double similarityThreshold) throws SLIB_Ex_Critic {

        Set<URI> results = new HashSet<URI>();
        results.add(startingConceptURI);
        Set<URI> processed = new HashSet<URI>();
        processed.add(startingConceptURI);
        Set<URI> toBeProcessed = new HashSet<URI>();
        toBeProcessed.add(startingConceptURI);

        URI currentConceptURI;
        double currentSimilarity;

        while (!toBeProcessed.isEmpty()) {
            currentConceptURI = toBeProcessed.iterator().next();
            toBeProcessed.remove(currentConceptURI);
            Set<URI> neighborURIs = sme.getGraph().getV(currentConceptURI, RDFS.SUBCLASSOF, Direction.BOTH);
            for (URI neighborURI : neighborURIs) {
                if (!processed.contains(neighborURI)) {
                    currentSimilarity = sme.computePairwiseSim(MeasuresConf.getMeasure(), startingConceptURI, neighborURI);
                    if (currentSimilarity > similarityThreshold) {
                        toBeProcessed.add(neighborURI);
                        results.add(neighborURI);
                    }
                }
            }
            processed.add(currentConceptURI);
        }
        return results;
    }
}