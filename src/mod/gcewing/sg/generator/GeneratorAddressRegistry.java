package gcewing.sg.generator;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import net.minecraft.world.World;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.yaml.snakeyaml.DumperOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class GeneratorAddressRegistry {

    private static final Map<String, Set<String>> addresses = new HashMap<>();
    private static final String WORLDS_NODE = "worlds";

    public static ConfigurationNode createRootNode(final Path path) throws IOException {
        final YAMLConfigurationLoader loader = YAMLConfigurationLoader.builder().setPath(path).setFlowStyle(DumperOptions.FlowStyle.BLOCK).build();

        if (Files.notExists(path)) {
            Files.createDirectories(path.getParent());
            Files.createFile(path);
            final ConfigurationNode node = loader.createEmptyNode(ConfigurationOptions.defaults());
            populateTemplate(node);
            loader.save(node);
        }

        return loader.load(ConfigurationOptions.defaults());
    }

    public static void populateAddresses(final ConfigurationNode root) {
        checkNotNull(root);

        addresses.clear();

        final ConfigurationNode worldsRoot = root.getNode(WORLDS_NODE);
        for (final Map.Entry<Object, ? extends ConfigurationNode> worldEntry : worldsRoot.getChildrenMap().entrySet()) {
            final String world = worldEntry.getKey().toString().toLowerCase();

            if (world.isEmpty()) {
                continue;
            }
            try {
                final List<String> worldAddresses = worldEntry.getValue().getList(TypeToken.of(String.class));
                addresses.put(world, Sets.newHashSet(worldAddresses));
            } catch (ObjectMappingException e) {
                e.printStackTrace();
            }
        }
    }

    public static void addAddress(final World world, final String address) {
        checkNotNull(world);
        checkNotNull(address);

        addresses.computeIfAbsent(world.getWorldInfo().getWorldName().toLowerCase(), k -> new HashSet<>()).add(address.toUpperCase());

        GeneratorAddressRegistry.writeAddresses();
    }

    public static boolean removeAddress(final World world, final String homeAddress) {
        checkNotNull(world);

        final Set<String> worldAddresses = addresses.getOrDefault(world.getWorldInfo().getWorldName().toLowerCase(), new HashSet<>());
        boolean removed = worldAddresses.removeIf(s -> s.equalsIgnoreCase(homeAddress));

        GeneratorAddressRegistry.writeAddresses();

        return removed;
    }

    public static boolean hasAddress(final World world, final String homeAddress) {
        checkNotNull(world);

        final Set<String> worldAddresses = addresses.getOrDefault(world.getWorldInfo().getWorldName().toLowerCase(), new HashSet<>());
        boolean exists = worldAddresses.contains(homeAddress.toUpperCase());

        return exists;
    }


    public static void writeAddresses() {
        final Path path = Paths.get(".", "config", "SGCraft", "generator.yml");
        final ConfigurationNode rootNode;
        try {
            rootNode = createRootNode(path);
            final ConfigurationNode worldsNode = rootNode.getNode(WORLDS_NODE);

            if (!addresses.isEmpty()) {
                addresses.forEach((key, value) -> worldsNode.getNode(key).setValue(Lists.newArrayList(value)));
            } else {
                populateTemplate(rootNode);
            }

            final YAMLConfigurationLoader loader = YAMLConfigurationLoader.builder().setPath(path).setFlowStyle(DumperOptions.FlowStyle.BLOCK).build();

            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
            }

            loader.save(rootNode);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String randomAddress(final World world, final String homeAddress, final Random random) {
        checkNotNull(world);
        checkNotNull(random);

        final Set<String> worldAddressesAll = addresses.getOrDefault(world.getWorldInfo().getWorldName().toLowerCase(), new HashSet<>());
        Set<String> worldAddresses = new HashSet<>(worldAddressesAll);

        boolean removed = worldAddresses.removeIf(s -> s.equalsIgnoreCase(homeAddress));

        if (worldAddresses.isEmpty()) {
            return null;
        }

        final int max = worldAddresses.size();
        final int rIndex = random.nextInt(max);

        final Iterator<String> iter = worldAddresses.iterator();
        int index = 0;

        while (iter.hasNext()) {
            final String address = iter.next();
            if (rIndex == index) {
                return Optional.of(address).get().toUpperCase();
            }
            index++;
        }

        return null;
    }

    private static void populateTemplate(final ConfigurationNode node) {
        // Simply writing out default template, don't mind me
        //node.getNode(WORLDS_NODE, "world").setValue(Lists.newArrayList("ABCDEFGHI"));
    }
}
