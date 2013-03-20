package org.purl.wf4ever.rosrs.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.purl.wf4ever.rosrs.client.evo.BaseTest;
import org.purl.wf4ever.rosrs.client.exception.ObjectNotLoadedException;
import org.purl.wf4ever.rosrs.client.exception.ROException;
import org.purl.wf4ever.rosrs.client.exception.ROSRSException;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

/**
 * A test of the annotation class.
 * 
 * @author piotrekhol
 * 
 */
public class AnnotationTest extends BaseTest {

    /** RO that will be mapped to local resources. */
    private static final URI RO_PREFIX = URI.create("http://example.org/ro1/");

    /** Annotation that will be mapped to local resources. */
    private static final URI ANN_PREFIX = URI.create("http://example.org/ro1/.ro/annotations/1");

    /** Annotation body that will be mapped to local resources. */
    private static final URI BODY_PREFIX = URI.create("http://example.org/ro1/body1.rdf");

    /** Some annotation available by HTTP. */
    private static final URI PUBLIC_ANNOTATION = URI
            .create("http://sandbox.wf4ever-project.org/rodl/ROs/AstronomyPack/.ro/annotations/a37e7a35-4918-4ffe-832f-442befa7d335");

    /** Some annotation body available by HTTP. */
    private static final URI PUBLIC_BODY = URI
            .create("http://sandbox.wf4ever-project.org/rodl/ROs/AstronomyPack/.ro/evo_info.ttl");
    /** Some annotation available by HTTP. */

    private static final URI PUBLIC_TARGET = URI.create("http://sandbox.wf4ever-project.org/rodl/ROs/AstronomyPack/");

    /** A loaded RO. */
    private static ResearchObject ro1;

    /** A loaded annotation. */
    private static Annotation an1;


    /**
     * Prepare a loaded RO.
     * 
     * @throws Exception
     *             when loading the RO fails
     */
    @BeforeClass
    public static void setUpBeforeClass()
            throws Exception {
        BaseTest.setUpBeforeClass();
        ro1 = new ResearchObject(RO_PREFIX, rosrs);
        ro1.load();
        an1 = new Annotation(ro1, ANN_PREFIX, BODY_PREFIX, RO_PREFIX, URI.create("http://test.myopenid.com"),
                new DateTime(2011, 12, 02, 16, 01, 10, DateTimeZone.UTC));
        an1.load();
    }


    /**
     * Test the constructor.
     */
    @Test
    public final void testAnnotationResearchObjectUriUriSetOfURIURIDateTime() {
        Set<URI> targets = new HashSet<>();
        targets.add(RO_PREFIX);
        Annotation annotation = new Annotation(ro1, ANN_PREFIX, BODY_PREFIX, targets,
                URI.create("http://test.myopenid.com"), new DateTime(2011, 12, 02, 16, 01, 10, DateTimeZone.UTC));
        Assert.assertFalse(annotation.isLoaded());
    }


    /**
     * Test the constructor.
     */
    @Test
    public final void testAnnotationResearchObjectUriUriUriUriDateTime() {
        Annotation annotation = new Annotation(ro1, ANN_PREFIX, BODY_PREFIX, RO_PREFIX,
                URI.create("http://test.myopenid.com"), new DateTime(2011, 12, 02, 16, 01, 10, DateTimeZone.UTC));
        Assert.assertFalse(annotation.isLoaded());
    }


    /**
     * Create an RO and add an annotation to it, then delete both.
     * 
     * @throws ROSRSException
     *             unexpected server response
     * @throws ROException
     *             the manifest is incorrect
     */
    @Test
    public final void testCreateDelete()
            throws ROSRSException, ROException {
        ResearchObject ro;
        try {
            ro = ResearchObject.create(rosrs, "JavaClientTest");
        } catch (ROSRSException e) {
            if (e.getStatus() == HttpStatus.SC_CONFLICT) {
                ro = new ResearchObject(rosrs.getRosrsURI().resolve("JavaClientTest/"), rosrs);
                ro.delete();
                ro = ResearchObject.create(rosrs, "JavaClientTest");
            } else {
                throw e;
            }
        }
        Resource r = ro.aggregate(BODY_PREFIX);
        Annotation an = Annotation.create(ro, r.getProxyUri(), ro.getUri());
        Assert.assertNotNull(an);
        an.delete();
        ro.delete();
    }


    /**
     * Load an annotation body over HTTP.
     * 
     * @throws ROSRSException
     *             unexpected server response
     * @throws IOException
     *             error serializing an annotation body
     */
    @Test
    public final void testLoad()
            throws ROSRSException, IOException {
        Annotation an = new Annotation(ro1, PUBLIC_ANNOTATION, PUBLIC_BODY, PUBLIC_TARGET, null, null);
        Assert.assertFalse(an.isLoaded());
        an.load();
        Assert.assertTrue(an.isLoaded());
    }


    /**
     * Test get RO.
     */
    @Test
    public final void testGetResearchObject() {
        Assert.assertEquals(ro1, an1.getResearchObject());
    }


    /**
     * The correct URI is returned.
     */
    @Test
    public final void testGetUri() {
        Assert.assertEquals(ANN_PREFIX, an1.getUri());
    }


    /**
     * The correct body URI is returned.
     */
    @Test
    public final void testGetBody() {
        Assert.assertEquals(BODY_PREFIX, an1.getBody());
    }


    /**
     * The correct creator is returned.
     */
    @Test
    public final void testGetCreator() {
        Assert.assertEquals(URI.create("http://test.myopenid.com"), an1.getCreator());
    }


    /**
     * The correct creation date is returned.
     */
    @Test
    public final void testGetCreated() {
        Assert.assertEquals(new DateTime(2011, 12, 02, 16, 01, 10, DateTimeZone.UTC), an1.getCreated());
    }


    /**
     * The correct list of targets is returned.
     */
    @Test
    public final void testGetTargets() {
        Set<URI> targets = new HashSet<>();
        targets.add(RO_PREFIX);
        Assert.assertEquals(targets, an1.getTargets());
    }


    /**
     * The correct loaded flag is returned.
     */
    @Test
    public final void testIsLoaded() {
        Assert.assertTrue(an1.isLoaded());
    }


    /**
     * The correct annotation body serialization is returned.
     * 
     * @throws IOException
     *             error serializing the annotation body
     */
    @Test
    public final void testGetBodySerializedAsString()
            throws IOException {
        String body = an1.getBodySerializedAsString();
        Assert.assertNotNull(body);

        Model ex = ModelFactory.createDefaultModel();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("ro1/body1.rdf")) {
            ex.read(in, BODY_PREFIX.toString());
        }

        Model res = ModelFactory.createDefaultModel();
        try (InputStream in = IOUtils.toInputStream(body)) {
            res.read(in, BODY_PREFIX.toString());
        }
        Assert.assertTrue(ex.isIsomorphicWith(res));
    }


    /**
     * The list of annotation statement should be fill up when the object is loaded.
     * 
     * @throws ROSRSException
     *             unexpected server response when downloading the body
     * @throws IOException
     *             error copying streams
     */
    @Test
    public final void testGetStatement()
            throws ROSRSException, IOException {
        Annotation annotation = new Annotation(ro1, PUBLIC_ANNOTATION, PUBLIC_BODY, PUBLIC_TARGET, null, null);
        annotation.load();
        Assert.assertNotNull(annotation.getStatements());
    }


    /**
     * When not loaded are accessed, ObjectNotLoadedException is thrown.
     * 
     * @throws ROSRSException
     *             unexpected server response when downloading the body
     * @throws IOException
     *             error copying streams
     */
    @Test(expected = ObjectNotLoadedException.class)
    public final void testGetNotLoadedStatement()
            throws ROSRSException, IOException {
        Annotation annotation = new Annotation(ro1, PUBLIC_ANNOTATION, PUBLIC_BODY, PUBLIC_TARGET, null, null);
        annotation.getStatements();
    }
}
