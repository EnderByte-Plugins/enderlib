package com.enderbyte.enderlib.database.migration;

import com.enderbyte.enderlib.database.api.exception.MigrationException;
import com.enderbyte.enderlib.database.api.migration.Migration;
import com.enderbyte.enderlib.database.api.migration.MigrationSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Factories for the usual migration sources.
 *
 * <p>Files are named {@code V<version>__<description>.sql}, for example
 * {@code V1__create_players.sql} or {@code V2__add_last_seen_index.sql}. The double underscore
 * separates the version from the description, and underscores in the description become spaces.
 */
public final class MigrationSources {

    private static final Pattern FILE_NAME =
            Pattern.compile("^V(\\d+)__(.+)\\.sql$", Pattern.CASE_INSENSITIVE);

    private MigrationSources() {
    }

    /**
     * Loads {@code .sql} files from a folder inside the plugin jar.
     *
     * <pre>{@code
     * // src/main/resources/migrations/V1__create_players.sql
     * database.migrations().source(MigrationSources.classpath("migrations")).migrateAndWait();
     * }</pre>
     *
     * @param folder resource folder, without a leading slash
     * @return the source
     */
    public static MigrationSource classpath(final String folder) {
        return classpath(folder, MigrationSources.class.getClassLoader());
    }

    /**
     * Loads {@code .sql} files from a classpath folder using an explicit loader.
     *
     * <p>Pass your plugin's own class loader: in a Minecraft server every plugin has its own, and
     * the library's loader cannot see your resources.
     *
     * @param folder      resource folder, without a leading slash
     * @param classLoader loader that can see the folder
     * @return the source
     */
    public static MigrationSource classpath(final String folder, final ClassLoader classLoader) {
        final String normalised = folder.endsWith("/") ? folder.substring(0, folder.length() - 1) : folder;
        return () -> {
            final List<Migration> migrations = new ArrayList<>(8);
            try {
                for (final URL root : Collections.list(classLoader.getResources(normalised))) {
                    readFrom(root, normalised, classLoader, migrations);
                }
            } catch (final IOException e) {
                throw new MigrationException("Could not list migrations in classpath folder '"
                        + normalised + '\'', e);
            }
            if (migrations.isEmpty()) {
                throw new MigrationException("No migrations found in classpath folder '" + normalised
                        + "'. Expected files named V1__description.sql. If your plugin is shaded,"
                        + " pass your own ClassLoader to MigrationSources.classpath(folder, loader).");
            }
            return migrations;
        };
    }

    /**
     * Loads {@code .sql} files from a directory on disk, typically the plugin's data folder, which
     * lets a server owner add a migration without rebuilding the jar.
     *
     * @param directory directory to scan
     * @return the source
     */
    public static MigrationSource directory(final Path directory) {
        return () -> {
            if (!Files.isDirectory(directory)) {
                throw new MigrationException("Migration directory does not exist: " + directory);
            }
            try (Stream<Path> files = Files.list(directory)) {
                final List<Migration> migrations = new ArrayList<>(8);
                for (final Path file : files.sorted().toList()) {
                    final Migration migration = parse(file.getFileName().toString(), () -> read(file));
                    if (migration != null) {
                        migrations.add(migration);
                    }
                }
                return migrations;
            } catch (final IOException e) {
                throw new MigrationException("Could not read migrations from " + directory, e);
            }
        };
    }

    /**
     * A source holding migrations built in code, useful for tests and for changes that need logic.
     *
     * @param migrations the migrations
     * @return the source
     */
    public static MigrationSource of(final Migration... migrations) {
        final List<Migration> copy = List.copyOf(Arrays.asList(migrations));
        return () -> copy;
    }

    /**
     * Combines several sources, for a plugin that ships migrations in its jar and also honours ones
     * dropped into its data folder.
     *
     * @param sources sources to merge, in order
     * @return the combined source
     */
    public static MigrationSource composite(final MigrationSource... sources) {
        final List<MigrationSource> copy = List.copyOf(Arrays.asList(sources));
        return () -> {
            final List<Migration> migrations = new ArrayList<>(16);
            for (final MigrationSource source : copy) {
                migrations.addAll(source.load());
            }
            return migrations;
        };
    }

    private static void readFrom(final URL root,
                                 final String folder,
                                 final ClassLoader classLoader,
                                 final List<Migration> migrations) throws IOException {
        final URI uri = URI.create(root.toString());
        if ("jar".equals(uri.getScheme())) {
            // A jar needs a mounted FileSystem to be walked; open one per jar and close it again.
            final String[] parts = uri.toString().split("!", 2);
            try (FileSystem jar = FileSystems.newFileSystem(URI.create(parts[0]), Map.of())) {
                collect(jar.getPath(folder), migrations);
            } catch (final RuntimeException e) {
                throw new MigrationException("Could not read migrations from " + uri, e);
            }
            return;
        }
        try {
            collect(Path.of(uri), migrations);
        } catch (final IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) {
            throw new MigrationException("Cannot read migrations from '" + uri
                    + "': this class loader exposes resources through a URL scheme that is not"
                    + " backed by a file system. Use MigrationSources.directory(path) or"
                    + " MigrationSources.of(...) instead.", e);
        }
    }

    private static void collect(final Path folder, final List<Migration> migrations) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (final Path file : files.sorted().toList()) {
                final Migration migration = parse(file.getFileName().toString(), () -> read(file));
                if (migration != null) {
                    migrations.add(migration);
                }
            }
        }
    }

    private static Migration parse(final String fileName, final ContentSupplier content) {
        final Matcher matcher = FILE_NAME.matcher(fileName);
        if (!matcher.matches()) {
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".sql")) {
                throw new MigrationException("Migration file '" + fileName
                        + "' does not follow the V<version>__<description>.sql convention,"
                        + " for example V1__create_players.sql");
            }
            return null;
        }
        final int version = Integer.parseInt(matcher.group(1));
        final String description = matcher.group(2).replace('_', ' ').trim();
        return new SqlMigration(version, description, content.get());
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    private interface ContentSupplier {

        String get();
    }
}
