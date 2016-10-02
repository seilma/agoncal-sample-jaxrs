package org.agoncal.sample.jaxrs.jwt.rest;

import org.agoncal.sample.jaxrs.jwt.domain.User;
import org.agoncal.sample.jaxrs.jwt.filter.JWTTokenNeeded;
import org.agoncal.sample.jaxrs.jwt.filter.JWTTokenNeededFilter;
import org.agoncal.sample.jaxrs.jwt.repository.UserRepository;
import org.agoncal.sample.jaxrs.jwt.util.KeyGenerator;
import org.agoncal.sample.jaxrs.jwt.util.LoggerProducer;
import org.agoncal.sample.jaxrs.jwt.util.PasswordUtils;
import org.agoncal.sample.jaxrs.jwt.util.SimpleKeyGenerator;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.net.URI;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Arquillian.class)
@RunAsClient
public class SecuredEchoEndpointTest {

    // ======================================
    // =             Attributes             =
    // ======================================

    private static final User TEST_USER = new User("id", "last name", "first name", "login", "password");
    private static String token;
    private Client client;
    private WebTarget securedEchoTarget;
    private WebTarget userTarget;

    // ======================================
    // =          Injection Points          =
    // ======================================

    @ArquillianResource
    private URI baseURL;

    // ======================================
    // =         Deployment methods         =
    // ======================================

    @Deployment(testable = false)
    public static WebArchive createDeployment() {

        // Import Maven runtime dependencies
        File[] files = Maven.resolver().loadPomFromFile("pom.xml")
                .importRuntimeDependencies().resolve().withTransitivity().asFile();

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(SecuredEchoEndpoint.class)
                .addClasses(User.class, UserRepository.class, UserEndpoint.class)
                .addClasses(JWTTokenNeededFilter.class, JWTTokenNeeded.class, KeyGenerator.class, SimpleKeyGenerator.class, PasswordUtils.class)
                .addClasses(LoggerProducer.class, SecuredEchoApplicationConfig.class)
                .addAsResource("META-INF/persistence-test.xml", "META-INF/persistence.xml")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsLibraries(files);
    }

    // ======================================
    // =          Lifecycle methods         =
    // ======================================

    @Before
    public void initWebTarget() {
        client = ClientBuilder.newClient();
        securedEchoTarget = client.target(baseURL).path("api/securedecho");
        userTarget = client.target(baseURL).path("api/users");
    }

    // ======================================
    // =            Test methods            =
    // ======================================

    @Test
    @InSequence(1)
    public void shouldFailCauseNoUserAuthentication() throws Exception {
        Response response = securedEchoTarget.request(TEXT_PLAIN).get();
        assertEquals(401, response.getStatus());
    }

    @Test
    @InSequence(2)
    public void shouldCreateUser() throws Exception {
        Response response = userTarget.request(APPLICATION_JSON_TYPE).post(Entity.entity(TEST_USER, APPLICATION_JSON_TYPE));
        assertEquals(201, response.getStatus());
    }

    @Test
    @InSequence(3)
    public void shouldLogUserIn() throws Exception {
        Form form = new Form();
        form.param("username", TEST_USER.getLogin());
        form.param("password", TEST_USER.getPassword());

        Response response = userTarget.path("login").request(MediaType.APPLICATION_JSON_TYPE).post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        assertEquals(200, response.getStatus());
        assertNotNull(response.getHeaderString(HttpHeaders.AUTHORIZATION));
        token = response.getHeaderString(HttpHeaders.AUTHORIZATION);
    }

    @Test
    @InSequence(4)
    public void shouldSucceedCauseTokenIsPassedInTheHeader() throws Exception {
        Response response = securedEchoTarget.request(TEXT_PLAIN).header(HttpHeaders.AUTHORIZATION, token).get();
        assertEquals(200, response.getStatus());
    }
}
