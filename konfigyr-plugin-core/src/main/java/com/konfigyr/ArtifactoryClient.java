package com.konfigyr;

import com.konfigyr.artifactory.Artifact;
import com.konfigyr.artifactory.ArtifactMetadata;
import com.konfigyr.artifactory.Manifest;
import com.konfigyr.artifactory.Release;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

/**
 * Client interface for communicating with the Konfigyr {@code Artifactory} REST API.
 * <p>
 * The {@code ArtifactoryClient} is responsible for exchanging configuration metadata, manifests,
 * and release information between Gradle build plugins and the Konfigyr backend services.
 * <p>
 * In the plugin ecosystem, the {@code ArtifactoryClient} acts as the bridge between the local
 * build environment and the remote Konfigyr Artifactory. It enables automated synchronization of
 * artifact metadata and detection of configuration drift between the build output and the registered
 * metadata repository.
 * <p>
 * Implementations of this client typically authenticate using OAuth2 <em>Client Credentials</em> obtained
 * from the Konfigyr Identity Provider. Each plugin or build agent is registered as a trusted client and scoped
 * to its associated Service within the namespace.
 * <p>
 * A typical workflow consists out of the following steps:
 * <ol>
 *   <li>
 *       The build plugin queries the {@link #getManifest()} endpoint to retrieve the current manifest for the service.
 *   </li>
 *   <li>
 *       The plugin computes the local artifact composition and compares it with the manifest to identify missing
 *       or outdated metadata.
 *   </li>
 *   <li>
 *       The plugin uploads only the new or changed metadata using {@link #upload(ArtifactMetadata)}.
 *   </li>
 *   <li>
 *       The Artifactory processes and persists the uploaded metadata, creating a new {@link Release} entry
 *       upon successful ingestion.
 *   </li>
 *   <li>
 *       Poll the backend using {@link #getRelease(Artifact)} until the release is in its final state.
 *   </li>
 * </ol>
 * <p>
 * Implementations should be thread-safe and resilient to transient HTTP or network errors.
 * Clients are typically instantiated once per Gradle build invocation.
 *
 * @author Vladimir Spasic
 * @since 1.0.0
 * @see Manifest
 * @see Release
 * @see ArtifactMetadata
 */
public interface ArtifactoryClient {

    /**
     * Retrieves the current {@link Manifest} for the service associated with the authenticated namespace
     * or OAuth client credentials.
     * <p>
     * The manifest represents the list of artifact versions currently registered in the Konfigyr {@code Artifactory}
     * for this service. It allows the plugin to detect which artifacts have already been uploaded, thereby
     * preventing redundant metadata submissions.
     * <p>
     * Implementations should perform an HTTP {@code GET} request to the following endpoint:
     * {@code /namespaces/{namespace}/services/{service}/manifest}.
     *
     * @return the current manifest, never {@literal null}.
     * @throws HttpResponseException if communication with the API fails or authentication is invalid.
     */
    @NonNull
    Manifest getManifest();

    /**
     * Publishes a new service release to Konfigyr {@code Artifactory} by submitting the set of artifacts
     * that contribute configuration metadata to the application.
     * <p>
     * The provided collection represents all artifacts discovered on the application's classpath that
     * expose Spring Boot configuration metadata. These artifacts are typically detected by the Konfigyr build
     * plugins during the build process by scanning dependency JARs for {@code spring-configuration-metadata.json}
     * descriptors or equivalent metadata sources.
     * <p>
     * When this method is invoked, the plugin sends the artifact coordinates to the Konfigyr platform where
     * the following operations occur:
     *
     * <ul>
     *     <li>
     *         The artifact coordinates ({@code groupId}, {@code artifactId}, {@code version})
     *         are validated and resolved against the Konfigyr Artifactory.
     *     </li>
     *     <li>
     *         The dependency set is used to create or update the service {@link Manifest}.
     *     </li>
     *     <li>
     *         The manifest establishes the relationship between the service and the artifacts
     *         that define its configuration properties.
     *     </li>
     *     <li>
     *         The {@code Artifactory} aggregates configuration metadata contributed by the
     *         referenced artifacts and prepares the effective property model used by the service.
     *     </li>
     * </ul>
     * <p>
     * This operation effectively represents a <em>service release</em> from the perspective
     * of the Konfigyr platform. Each publish operation updates the service dependency manifest and
     * allows Konfigyr to recompute the configuration property definitions available to the service.
     * <p>
     * Implementations should perform an HTTP {@code GET} request to the following endpoint:
     * {@code /namespaces/{namespace}/services/{service}/manifest}.
     *
     * @param artifacts the collection of artifacts discovered in the service classpath that
     *                  provide Spring Boot configuration metadata. Each artifact must contain
     *                  valid Maven coordinates. The collection must not be {@literal null},
     *                  but may be empty if no configuration metadata providers are detected.
     * @return the resulting {@link Manifest} associated with the published service release
     * @throws HttpResponseException if communication with the API fails or authentication is invalid.
     */
    Manifest publish(Collection<? extends Artifact> artifacts);

    /**
     * Checks if the property metadata for a specific artifact version is already released.
     * <p>
     * Implementations should perform an HTTP {@code HEAD} request to the following endpoint:
     * {@code /artifacts/{groupId}/{artifactId}/{version}}.
     * <p>
     * If the HTTP response fails with a {@code 404} status code, the artifact version is not released.
     *
     * @param artifact the artifact for which the release should be checked, never {@literal null}.
     * @return {@literal true} if the artifact version is released, {@literal false} otherwise.
     */
    boolean isReleased(@NonNull Artifact artifact);

    /**
     * Uploads configuration property metadata for a specific artifact version to the Konfigyr {@code Artifactory}.
     * <p>
     * This operation registers or updates configuration metadata in the {@code Artifactory} registry, associating
     * it with an existing artifact version or creating a new one if necessary.
     * <p>
     * Implementations should perform an HTTP {@code POST} request to the following endpoint:
     * {@code /artifacts/{groupId}/{artifactId}/{version}}.
     * <p>
     * If the upload results in a new release (e.g., new metadata or artifact version), the server responds with
     * a {@link Release} describing the persisted version.
     *
     * @param metadata the artifact metadata payload to upload, must not be {@literal null}.
     * @return the resulting {@link Release} entry describing the updated artifact metadata, never {@literal null}.
     * @throws HttpResponseException if the upload fails, validation fails, or network errors occur.
     */
    @NonNull
    Release upload(@NonNull ArtifactMetadata metadata);

    /**
     * Retrieves the current {@link Release} state for the specified {@link Artifact}.
     * <p>
     * This method allows the build plugins to check whether an uploaded release has finished processing.
     * It can be used in polling loops to wait for the batch job to complete.
     * <p>
     * The returned {@link Release} will include status fields such as:
     * <ul>
     *   <li>{@code PENDING}: metadata is still being processed by the {@code Artifactory}.</li>
     *   <li>{@code COMPLETED}: metadata successfully processed and persisted.</li>
     *   <li>{@code FAILED}: the artifact release job failed or metadata was invalid.</li>
     * </ul>
     * <p>
     * Implementations should perform an HTTP {@code GET} request to the following endpoint:
     * {@code /artifacts/{groupId}/{artifactId}/{version}}.
     * </p>
     *
     * @param artifact the artifact for which the release should be retrieved, never {@literal null}.
     * @return the current {@link Release} state for the specified artifact, never {@literal null}.
     * @throws HttpResponseException if the upload fails, validation fails, or network errors occur.
     */
    @NonNull
    Release getRelease(@NonNull Artifact artifact);
}
