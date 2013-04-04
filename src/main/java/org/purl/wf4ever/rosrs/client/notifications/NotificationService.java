package org.purl.wf4ever.rosrs.client.notifications;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.openrdf.rio.RDFFormat;
import org.purl.wf4ever.rosrs.client.evo.ROEVOService;
import org.purl.wf4ever.rosrs.client.exception.NotificationsException;

import com.damnhandy.uri.template.UriTemplate;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.shared.JenaException;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

/**
 * The notification service.
 * 
 * @see http://wf4ever-project.org/wiki/display/docs/Showcase+129.+The+notification+service
 * @author piotrekhol
 * 
 */
public class NotificationService implements Serializable {

    /** id. */
    private static final long serialVersionUID = 4882901036606057893L;

    /** Logger. */
    private static final Logger LOGGER = Logger.getLogger(ROEVOService.class);

    /** Notifications service URI. */
    private URI serviceUri;

    /** OAuth 2 access token. */
    private String token;

    /** HTTP client. */
    private transient Client client;

    /** Notifications resource URI template. */
    private String notificationsUriTemplateString;


    /**
     * Constructor.
     * 
     * @param serviceUri
     *            Notification service URI
     * @param token
     *            RODL access token
     */
    public NotificationService(URI serviceUri, String token) {
        this.serviceUri = serviceUri;
        this.token = token;
    }


    /**
     * Return an HTTP client, creating it if necessary.
     * 
     * @return an HTTP client
     */
    private Client getClient() {
        if (client == null) {
            client = Client.create();
        }
        return client;
    }


    /**
     * Load the RO EVO service description.
     */
    private void init() {
        try {
            InputStream serviceDesc = getClient().resource(serviceUri).accept(RDFFormat.RDFXML.getDefaultMIMEType())
                    .get(InputStream.class);
            Model model = ModelFactory.createDefaultModel();
            model.read(serviceDesc, serviceUri.toString());
            Resource roevo = model.getResource(serviceUri.toString());
            this.notificationsUriTemplateString = roevo
                    .listProperties(pl.psnc.dl.wf4ever.vocabulary.NotificationService.notifications).next().getObject()
                    .asLiteral().getString();
        } catch (JenaException e) {
            LOGGER.warn("Could not initialize the notification service: " + e.getLocalizedMessage());
        }
    }


    /**
     * Expand the URI template with the provided values.
     * 
     * @param researchObjectUri
     *            the research object URI. If set, only notifications related to this RO will be returned.
     * @param from
     *            the timestamp of the oldest notification that should be returned.
     * @param to
     *            the timestamp of the most recent notification that should be returned.
     * @return the URI for the notifications feed
     */
    public URI getNotificationsUri(URI researchObjectUri, DateTime from, DateTime to) {
        if (notificationsUriTemplateString == null) {
            init();
        }
        UriTemplate uriTemplate = UriTemplate.fromTemplate(notificationsUriTemplateString);
        if (researchObjectUri != null) {
            uriTemplate = uriTemplate.set("ro", researchObjectUri.toString());
        }
        if (from != null) {
            uriTemplate = uriTemplate.set("from", ISODateTimeFormat.dateTime().print(from));
        }
        if (to != null) {
            uriTemplate = uriTemplate.set("to", ISODateTimeFormat.dateTime().print(to));
        }
        return UriBuilder.fromUri(uriTemplate.expand()).build();
    }


    /**
     * Parse a notifications feed.
     * 
     * @param feedInputStream
     *            input stream with a feed in Atom format.
     * @return a list of notifications provided as Atom feed entries
     * @throws NotificationsException
     *             when the feed is invalid
     */
    public List<Notification> getNotifications(InputStream feedInputStream)
            throws NotificationsException {
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed;
        try {
            feed = input.build(new XmlReader(feedInputStream));
        } catch (IllegalArgumentException | FeedException | IOException e1) {
            throw new NotificationsException("Error when loading the search results", e1);
        }

        @SuppressWarnings("unchecked")
        List<SyndEntry> entries = feed.getEntries();
        List<Notification> notifications = new ArrayList<>();
        for (SyndEntry entry : entries) {
            String id = entry.getUri();
            String title = entry.getTitle();
            String content = entry.getDescription().getValue();
            Notification notification = new Notification(id, title, content);
            notification.setPublished(new DateTime(entry.getPublishedDate()));
            //TODO check what is returned
            List<Object> links = entry.getLinks();

            notifications.add(notification);
        }
        return notifications;
    }


    /**
     * Build a notifications feed URI, retrieve it and parse it.
     * 
     * @param researchObjectUri
     *            the research object URI. If set, only notifications related to this RO will be returned.
     * @param from
     *            the timestamp of the oldest notification that should be returned.
     * @param to
     *            the timestamp of the most recent notification that should be returned.
     * @return a list of notifications provided as Atom feed entries
     * @throws NotificationsException
     *             when the feed is invalid
     */
    public List<Notification> getNotifications(URI researchObjectUri, DateTime from, DateTime to)
            throws NotificationsException {
        URI feedUri = getNotificationsUri(researchObjectUri, from, to);
        WebResource webResource = getClient().resource(feedUri);
        Builder builder = webResource.accept(MediaType.APPLICATION_ATOM_XML_TYPE);
        if (token != null) {
            builder = builder.header("Authorization", "Bearer " + token);
        }
        try (InputStream feedInputStream = builder.get(InputStream.class)) {
            return getNotifications(feedInputStream);
        } catch (IOException e) {
            LOGGER.error("Can't close the feed input stream", e);
            return null;
        }
    }
}
