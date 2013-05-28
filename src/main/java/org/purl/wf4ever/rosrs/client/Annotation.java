package org.purl.wf4ever.rosrs.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.openrdf.rio.RDFFormat;
import org.purl.wf4ever.rosrs.client.exception.ObjectNotLoadedException;
import org.purl.wf4ever.rosrs.client.exception.ROSRSException;

import pl.psnc.dl.wf4ever.vocabulary.ORE;

import com.google.common.collect.Multimap;
import com.hp.hpl.jena.ontology.Individual;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.shared.JenaException;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.sun.jersey.api.client.ClientResponse;

/**
 * ro:AggregatedAnnotation.
 * 
 * @author piotrekhol
 * 
 */
public class Annotation extends Thing {

    /** id. */
    private static final long serialVersionUID = 2231042343400055586L;

    /** Logger. */
    private static final Logger LOG = Logger.getLogger(Annotation.class);

    /** RO aggregating the annotation. */
    private ResearchObject researchObject;

    /** annotation body, may be aggregated or not, may be a ro:Resource (rarely) or not. */
    private URI body;

    /** annotated resources, must be RO/aggregated resources/proxies. */
    private Set<URI> targets;

    /** has the annotation body been loaded. */
    private boolean loaded;

    /** annotation body serialized as string. */
    private String bodySerializedAsString;

    /** statements in the annotation body. */
    private List<Statement> statements;


    /**
     * Constructor.
     * 
     * @param researchObject
     *            RO aggregating the annotation
     * @param uri
     *            annotation URI
     * @param body
     *            annotation body, may be aggregated or not, may be a ro:Resource (rarely) or not
     * @param targets
     *            annotated resources, must be RO/aggregated resources/proxies
     * @param creator
     *            annotation author
     * @param created
     *            annotation creation time
     */
    public Annotation(ResearchObject researchObject, URI uri, URI body, Set<URI> targets, URI creator, DateTime created) {
        super(uri, creator, created);
        this.researchObject = researchObject;
        this.body = body;
        this.targets = targets;
        this.loaded = false;
    }


    /**
     * Constructor.
     * 
     * @param researchObject
     *            RO aggregating the annotation
     * @param uri
     *            annotation URI
     * @param body
     *            annotation body, may be aggregated or not, may be a ro:Resource (rarely) or not
     * @param target
     *            annotated resource, must be the RO/aggregated resource/proxy
     * @param creator
     *            annotation author
     * @param created
     *            annotation creation time
     */
    public Annotation(ResearchObject researchObject, URI uri, URI body, URI target, URI creator, DateTime created) {
        this(researchObject, uri, body, new HashSet<URI>(Arrays.asList(new URI[] { target })), creator, created);
    }


    /**
     * Create a new annotation. Does not add the annotation instance to the {@link ResearchObject} instance.
     * 
     * @param researchObject
     *            RO aggregating the annotation
     * @param body
     *            annotation body, may be aggregated or not, may be a ro:Resource (rarely) or not
     * @param targets
     *            annotated resources, must be RO/aggregated resources/proxies
     * @return created annotation
     * @throws ROSRSException
     *             unexpected response from the server
     */
    public static Annotation create(ResearchObject researchObject, URI body, Set<URI> targets)
            throws ROSRSException {
        ClientResponse response = researchObject.getRosrs().addAnnotation(researchObject.getUri(), targets, body);
        Multimap<String, URI> links = Utils.getLinkHeaders(response.getHeaders().get("Link"));
        Collection<URI> annUris = links.get(ORE.proxyFor.getURI());
        URI annUri = !annUris.isEmpty() ? annUris.iterator().next() : response.getLocation();
        OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM);
        model.read(response.getEntityInputStream(), null);
        response.close();

        Individual r = model.getIndividual(annUri.toString());
        com.hp.hpl.jena.rdf.model.Resource creatorNode = r.getPropertyResourceValue(DCTerms.creator);
        URI resCreator = creatorNode != null && creatorNode.isURIResource() ? URI.create(creatorNode.asResource()
                .getURI()) : null;
        RDFNode createdNode = r.getPropertyValue(DCTerms.created);
        DateTime resCreated = createdNode != null && createdNode.isLiteral() ? DateTime.parse(createdNode.asLiteral()
                .getString()) : null;

        return new Annotation(researchObject, response.getLocation(), body, targets, resCreator, resCreated);
    }


    /**
     * Create a new annotation. Does not add the annotation instance to the {@link ResearchObject} instance.
     * 
     * @param researchObject
     *            RO aggregating the annotation
     * @param body
     *            annotation body, may be aggregated or not, may be a ro:Resource (rarely) or not
     * @param target
     *            annotated resource, must be RO/aggregated resource/proxy
     * @return created annotation
     * @throws ROSRSException
     *             unexpected response from the server
     */
    public static Annotation create(ResearchObject researchObject, URI body, URI target)
            throws ROSRSException {
        return create(researchObject, body, new HashSet<URI>(Arrays.asList(new URI[] { target })));
    }


    /**
     * Deletes the annotation and the body.
     * 
     * @throws ROSRSException
     *             unexpected response from the server
     */
    public void delete()
            throws ROSRSException {
        researchObject.getRosrs().deleteAnnotationAndBody(uri);
        loaded = false;
        researchObject.removeAnnotation(this);
    }


    /**
     * Load the annotation body.
     * 
     * @throws ROSRSException
     *             unexpected server response when downloading the body
     */
    public void load()
            throws ROSRSException {
        OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        ClientResponse response = researchObject.getRosrs().getResource(body, "application/rdf+xml");
        try {
            model.read(response.getEntityInputStream(), body.toString());
        } catch (JenaException e) {
            throw new ROSRSException("Can't load annotation", HttpStatus.SC_NOT_FOUND, "Not found");
        } finally {
            try {
                response.getEntityInputStream().close();
            } catch (IOException e) {
                LOG.warn("Failed to close the annotation body input stream", e);
            }
        }

        //first, extract statements
        this.statements = extractStatements(model);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray())) {
                model.write(out, "RDF/XML");
                bodySerializedAsString = out.toString();
                loaded = true;
            }
        } catch (IOException e) {
            LOG.error("Could not close streams", e);
        }
    }


    /**
     * Create an list of statements for a model.
     * 
     * @param model
     *            Jena model
     * @return list of {@link Statement}
     */
    public List<Statement> extractStatements(Model model) {
        List<Statement> statements2 = new ArrayList<Statement>();
        for (com.hp.hpl.jena.rdf.model.Statement statement : model.listStatements().toSet()) {
            try {
                statements2.add(new Statement(statement, this));
            } catch (URISyntaxException e) {
                LOG.error("Could not parse statement", e);
            }
        }
        Collections.sort(statements2, new Comparator<Statement>() {

            @Override
            public int compare(Statement s1, Statement s2) {
                return s1.getPropertyLocalName().compareTo(s2.getPropertyLocalName());
            }
        });
        return statements2;
    }


    /**
     * Convert an annotation body (a list of {@link Statement}) to an RDF graph.
     * 
     * @param statements
     *            a list of {@link Statement}
     * @return input stream of an RDF graph in RDF/XML format
     */
    public static InputStream wrapAnnotationBody(List<Statement> statements) {
        Model body = ModelFactory.createDefaultModel();
        for (Statement stmt : statements) {
            body.add(stmt.createJenaStatement());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.write(out);
        return new ByteArrayInputStream(out.toByteArray());
    }


    public ResearchObject getResearchObject() {
        return researchObject;
    }


    public URI getBody() {
        return body;
    }


    public Set<URI> getTargets() {
        return targets;
    }


    public boolean isLoaded() {
        return loaded;
    }


    public String getBodySerializedAsString() {
        return bodySerializedAsString;
    }


    /**
     * Get a list of statements in the annotation body.
     * 
     * @return a list of statements
     * @throws ObjectNotLoadedException
     *             if the annotation body wasn't loaded
     */
    public List<Statement> getStatements()
            throws ObjectNotLoadedException {
        if (!loaded) {
            throw new ObjectNotLoadedException("the annotation wasn't loaded: " + uri);
        }
        return statements;
    }


    /**
     * Return all property values that describe the given resource and are literals.
     * 
     * @param resource
     *            the subject
     * @param property
     *            the property
     * @return a (possibly empty) list of literal values
     * @throws ROSRSException
     *             unexpected server response when downloading the body
     */
    public List<String> getPropertyValues(Annotable resource, URI property)
            throws ROSRSException {
        if (!isLoaded()) {
            load();
        }
        List<String> literals = new ArrayList<>();
        for (Statement statement : getStatements()) {
            if (statement.getSubjectURI().equals(resource.getUri()) && statement.getPropertyURI().equals(property)
                    && statement.isObjectLiteral()) {
                literals.add(statement.getObjectValue());
            }
        }
        return literals;
    }


    /**
     * Update the list of statements by setting the property value to a given literal value. All other literal values of
     * this property describing this resource are removed.
     * 
     * @param resource
     *            the subject
     * @param property
     *            the URI of the property
     * @param value
     *            the value to be used as a literal
     */
    public void updatePropertyValue(Annotable resource, URI property, String value) {
        deletePropertyValue(resource, property);
        statements.add(new Statement(resource.getUri(), property, value));
    }


    /**
     * Delete all literal values of a property describing a resource from the list of statements. Property values that
     * are not literals are ignored (preserved).
     * 
     * @param resource
     *            the subject
     * @param property
     *            the URI of the property
     */
    public void deletePropertyValue(Annotable resource, URI property) {
        for (Statement statement : new ArrayList<>(getStatements())) {
            if (statement.getSubjectURI().equals(resource.getUri()) && statement.getPropertyURI().equals(property)
                    && statement.isObjectLiteral()) {
                statements.remove(statement);
            }
        }
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((body == null) ? 0 : body.hashCode());
        result = prime * result + ((created == null) ? 0 : created.hashCode());
        result = prime * result + ((creator == null) ? 0 : creator.hashCode());
        result = prime * result + ((uri == null) ? 0 : uri.hashCode());
        return result;
    }


    @Override
    public String toString() {
        return uri.toString();
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Annotation other = (Annotation) obj;
        if (body == null) {
            if (other.body != null) {
                return false;
            }
        } else if (!body.equals(other.body)) {
            return false;
        }
        if (created == null) {
            if (other.created != null) {
                return false;
            }
        } else if (!created.equals(other.created)) {
            return false;
        }
        if (creator == null) {
            if (other.creator != null) {
                return false;
            }
        } else if (!creator.equals(other.creator)) {
            return false;
        }
        if (uri == null) {
            if (other.uri != null) {
                return false;
            }
        } else if (!uri.equals(other.uri)) {
            return false;
        }
        return true;
    }


    /**
     * Update the annotation body.
     * 
     * @throws ROSRSException
     *             unexpected response from the server
     * 
     */
    public void update()
            throws ROSRSException {
        InputStream bodyContent = wrapAnnotationBody(getStatements());
        researchObject.getRosrs().updateResource(getBody(), bodyContent, RDFFormat.RDFXML.getDefaultMIMEType());
    }

}
