package com.konfigyr;

import com.konfigyr.artifactory.*;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;

/**
 * Client interface for communicating with the Konfigyr REST API.
 * <p>
 * Implementations authenticate using OAuth2 <em>Client Credentials</em> issued by the Konfigyr
 * Identity Provider. Implementations should be thread-safe and resilient to transient HTTP or
 * network errors; a single instance is typically shared across an entire build invocation spanning
 * multiple projects or modules, reusing one authenticated connection rather than one per project.
 * <p>
 * This interface supports two distinct publishing workflows. Which one applies to a given artifact
 * depends on whether the publishing namespace's {@code groupId} has been verified:
 *
 * <h2>Publishing first-party metadata via a Service Release</h2>
 * <p>
 * Used for artifacts not covered by a verified {@code groupId}. Metadata published this way is
 * scoped to a single namespace and service, it is not added to the shared Artifactory registry.
 * Because a single client instance may be shared across many services (e.g. the modules of a
 * multi-module build), the target {@code namespace} and {@code service} are supplied as
 * arguments to each call below rather than fixed once for the client's lifetime.
 * <ol>
 *   <li>Call {@link #release(String, String, Collection)} with every artifact discovered on the
 *       classpath that exposes Spring Boot configuration metadata, each paired with a locally
 *       computed checksum. The returned {@link ServiceRelease} reports, per
 *       {@link ServiceReleaseEntry}, whether that artifact still needs its metadata uploaded.</li>
 *   <li>Call {@link #upload(String, String, ServiceRelease, ArtifactMetadata)} for every artifact
 *       whose entry requires it. Artifacts already covered by the Artifactory, or matching a
 *       checksum already uploaded for this release, do not need to be uploaded again.</li>
 *   <li>Call {@link #complete(String, String, ServiceRelease)} once every required upload has
 *       succeeded. This becomes the service's current {@link Manifest}.</li>
 * </ol>
 *
 * <h2>Publishing directly to the Artifactory</h2>
 * <p>
 * Used once the publishing namespace holds a verified claim on the artifact's {@code groupId},
 * metadata published this way becomes globally reusable, not scoped to one service or namespace.
 * <ol>
 *   <li>Call {@link #publish(ArtifactMetadata)} to submit the metadata. The Artifactory processes
 *       it asynchronously, creating a new {@link Publication} in the {@code PENDING} state.</li>
 *   <li>Poll {@link #getPublication(Artifact)} (or check {@link #isPublished(Artifact)}) until the
 *       publication reaches {@code PUBLISHED} or {@code FAILED}.</li>
 * </ol>
 * <p>
 * {@link #getManifest(String, String)} is independent of both workflows, it retrieves a service's
 * currently published manifest and does not need to be called before publishing.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see Manifest
 * @see ServiceRelease
 * @see ArtifactMetadata
 * @see Publication
 */
@NullMarked
public interface ArtifactoryClient {

    /**
     * Retrieves the {@link Manifest} currently published for the given service.
     * <p>
     * The manifest reflects the last release completed via {@link #complete(String, String, ServiceRelease)},
     * the artifacts and configuration metadata currently in effect for this service, regardless of
     * whether each entry's {@link ManifestEntry#source()} is {@code LOCAL} (uploaded directly for
     * this service) or {@code ARTIFACTORY} (resolved from the shared registry). This method does not
     * need to be called before publishing, {@link #release(String, String, Collection)} performs its
     * own server-side resolution of what needs uploading.
     * <p>
     * Implementations should perform an HTTP {@code GET} request to the following endpoint:
     * {@code /namespaces/{namespace}/services/{service}/manifest}.
     *
     * @param namespace the namespace owning the service, must not be {@literal null} or blank.
     * @param service the service whose manifest should be retrieved, must not be {@literal null} or blank.
     * @return the current manifest, never {@literal null}.
     * @throws HttpResponseException if communication with the API fails or authentication is invalid.
     */
    Manifest getManifest(String namespace, String service);

    /**
     * Creates a new {@link ServiceRelease} to Konfigyr by submitting the set of release candidate artifacts
     * that contribute configuration metadata to the application.
     * <p>
     * The provided collection represents all artifacts discovered on the application's classpath that
     * expose Spring Boot configuration metadata. These artifacts are typically detected by the Konfigyr build
     * plugins during the build process by scanning dependency JARs for {@code spring-configuration-metadata.json}
     * descriptors or equivalent metadata sources.
     * <p>
     * When this method is invoked, the plugin sends the artifact coordinates and the configuration metadata
     * checksum to the Konfigyr platform where the following operations occur:
     *
     * <ul>
     *     <li>
     *         The artifact coordinates ({@code groupId}, {@code artifactId}, {@code version})
     *         are validated and resolved against the Konfigyr Artifactory.
     *     </li>
     *     <li>
     *         The dependency set is used to create a new or update an existing {@link ServiceRelease}
     *         that is not yet marked as complete.
     *     </li>
     *     <li>
     *         The release establishes the relationship between the service and the artifacts
     *         that define its configuration properties.
     *     </li>
     *     <li>
     *         The {@link ServiceReleaseEntry Service release entries} from the resulting release
     *         instruct the plugin which {@link ArtifactMetadata} should be uploaded in order to
     *         rebuild the Service Configuration Catalog and it's {@link Manifest}.
     *     </li>
     * </ul>
     * <p>
     * This operation effectively represents a <em>service release</em> from the perspective
     * of the Konfigyr platform. Each publish operation updates the service dependency manifest and
     * allows Konfigyr to recompute the configuration property definitions available to the service.
     * <p>
     * Implementations should perform an HTTP {@code POST} request to the following endpoint:
     * {@code /namespaces/{namespace}/services/{service}/releases}.
     *
     * @param namespace the namespace owning the service, must not be {@literal null} or blank.
     * @param service the service this release is opened for, must not be {@literal null} or blank.
     * @param candidates the collection of candidate artifacts discovered in the service classpath
     *                   that provide Spring Boot configuration metadata. Each artifact must contain
     *                   valid Maven coordinates and a metadata checksum. The collection must not be
     *                   {@literal null}, but may be empty if no configuration metadata providers
     *                   are detected.
     * @return the opened or resumed {@link ServiceRelease}, reporting per-artifact upload status via
     *         {@link ServiceReleaseEntry}; never {@literal null}.
     * @throws HttpResponseException if communication with the API fails or authentication is invalid.
     */
    ServiceRelease release(String namespace, String service, Collection<? extends ServiceReleaseCandidate> candidates);

    /**
     * Uploads the Spring Boot configuration metadata for a single artifact that was declared as a
     * candidate when the given {@link ServiceRelease} was opened via
     * {@link #release(String, String, Collection)}.
     * <p>
     * The coordinates carried by {@code metadata} must match one of that release's candidates,
     * uploading metadata for an undeclared artifact fails. Only artifacts whose
     * {@link ServiceReleaseEntry} indicates an upload is required need to be submitted; artifacts
     * already covered by the Artifactory, or matching a checksum already uploaded for this release,
     * do not.
     * <p>
     * The operation is idempotent: uploading the same coordinates again replaces the metadata
     * previously submitted for that artifact within this release.
     * <p>
     * Implementations should perform an HTTP {@code POST} request, with {@code metadata} serialized
     * as the JSON request body, to the following endpoint:
     * {@code /namespaces/{namespace}/services/{service}/releases/{release}/artifacts}. The artifact's
     * coordinates are carried by the request body, not the URL.
     * <p>
     * On success the server responds with no content, hence the {@code void} return type, there is
     * nothing further for the caller to act on.
     *
     * @param namespace the namespace owning the service, must not be {@literal null} or blank.
     * @param service the service this release belongs to, must not be {@literal null} or blank.
     * @param release  the service release this upload contributes to, must not be {@literal null}.
     * @param metadata the artifact metadata payload to upload, must not be {@literal null}.
     * @throws HttpResponseException if the artifact was not declared as a candidate for this release,
     *                                the release no longer accepts uploads (already released), the
     *                                metadata payload is invalid, or communication with the API fails.
     */
    void upload(String namespace, String service, ServiceRelease release, ArtifactMetadata metadata);

    /**
     * Completes the given {@link ServiceRelease}, promoting it to the service's current
     * {@link Manifest}.
     * <p>
     * Every candidate declared when the release was opened via {@link #release(String, String, Collection)}
     * must already be resolved: artifacts the Artifactory covers need no action, and every other
     * {@link ServiceReleaseEntry} must have had its metadata submitted via
     * {@link #upload(String, String, ServiceRelease, ArtifactMetadata)}. If any declared artifact is
     * still missing its metadata, the release is not completed and this call fails.
     * <p>
     * On success this becomes the service's current manifest, the next call to
     * {@link #getManifest(String, String)} reflects the artifacts and metadata submitted in this release.
     * <p>
     * Implementations should perform an HTTP {@code POST} request to the following endpoint:
     * {@code /namespaces/{namespace}/services/{service}/releases/{release}/complete}.
     *
     * @param namespace the namespace owning the service, must not be {@literal null} or blank.
     * @param service the service this release belongs to, must not be {@literal null} or blank.
     * @param release the release to complete, must not be {@literal null}.
     * @return the completed release, with {@link ServiceRelease#state()} {@code RELEASED} and
     *         {@link ServiceRelease#publishedAt()} set; never {@literal null}.
     * @throws HttpResponseException if any declared artifact is still missing its metadata, the
     *                                release was already completed, or communication with the API
     *                                fails.
     */
    ServiceRelease complete(String namespace, String service, ServiceRelease release);

    /**
     * Checks if the property metadata for a specific artifact version is already published.
     * <p>
     * Implementations should perform an HTTP {@code HEAD} request to the following endpoint:
     * {@code /artifacts/{groupId}/{artifactId}/{version}}.
     * <p>
     * If the HTTP response fails with a {@code 404} status code, the artifact version is not released.
     *
     * @param artifact the artifact for which the publication should be checked, never {@literal null}.
     * @return {@literal true} if the artifact version is published, {@literal false} otherwise.
     */
    boolean isPublished(Artifact artifact);

    /**
     * Uploads the configuration property metadata for a specific artifact version to the Konfigyr
     * {@code Artifactory} and creates a new {@link Publication}.
     * <p>
     * This operation registers Spring configuration metadata in the {@code Artifactory} registry, associating
     * it with an existing artifact and creating a new version of it.
     * <p>
     * Implementations should perform an HTTP {@code POST} request to the following endpoint:
     * {@code /artifacts/{groupId}/{artifactId}/{version}}.
     * <p>
     * When the upload is successful, the server responds with a {@link Publication} describing the
     * persisted version.
     *
     * @param metadata the artifact metadata payload to upload, must not be {@literal null}.
     * @return the resulting {@link Publication} entry describing the uploaded artifact metadata, never {@literal null}.
     * @throws HttpResponseException if the upload fails, validation fails, or network errors occur.
     */
    Publication publish(ArtifactMetadata metadata);

    /**
     * Retrieves the current {@link Publication} state for the specified {@link Artifact}.
     * <p>
     * This method allows the build plugins to check whether the configuration metadata publication
     * task has finished processing. It can be used in polling loops to wait for the batch job to complete.
     * <p>
     * The returned {@link Publication} will include status fields such as:
     * <ul>
     *   <li>{@code PENDING}: metadata is still being processed by the {@code Artifactory}.</li>
     *   <li>{@code PUBLISHED}: metadata successfully processed and persisted.</li>
     *   <li>{@code FAILED}: the artifact release job failed or metadata was invalid.</li>
     * </ul>
     * <p>
     * Implementations should perform an HTTP {@code GET} request to the following endpoint:
     * {@code /artifacts/{groupId}/{artifactId}/{version}}.
     * </p>
     *
     * @param artifact the artifact for which the publication should be retrieved, never {@literal null}.
     * @return the current {@link Publication} state for the specified artifact, never {@literal null}.
     * @throws HttpResponseException if communication with the API fails or authentication is invalid.
     */
    Publication getPublication(Artifact artifact);
}
