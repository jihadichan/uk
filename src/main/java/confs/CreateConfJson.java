package confs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import confs.MnemonicsCsvLoader.KanjiMnemonicData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * This create the confs.json which is used to render the !conf lines into the kanji cards
 */
public class CreateConfJson {

    private static final List<Path> uberKanjiExportPaths = List.of(
            Paths.get("yomichan/deck/UberKanji.txt")
    );
    private static final AtomicInteger groupIndex = new AtomicInteger();
    private static final Map<String, Integer> confGroupRefs = new HashMap<>(); // key=kanji, val=id of group
    //    private static final Map<Integer, Map<String, KanjiMnemonicData>> confGroups = new HashMap<>(); // key=kanji, val=Map of readings,meaning
    private static final Map<String, KanjiMnemonicData> kanjiDataMap = new MnemonicsCsvLoader(uberKanjiExportPaths).getKanjiMnemonicDataMap(); // key=kanji, val=reading,meaning
    private static final Map<String, KanjiConfData> confDataMap = new LinkedHashMap<>();
    private static final Pattern kanjiPattern = Pattern.compile("^\\p{InCJK_Unified_Ideographs}$");
    private static final Path confsFolder = Paths.get("confs");
    private static final Gson GSON = new GsonBuilder().create();

    public static void main(final String[] args) throws Exception {
        final List<String> confs = loadFileAsList(confsFolder.resolve("confList"));
        confs.forEach(line -> {
            final String[] kanjis = extractKanji(line);
            createConfDataMap(kanjis);
        });
//        aggregateMissingConfs();

        // Write confs.js
        final String confJsAsString = "var confMap = " + GSON.toJson(confDataMap) + ";";
        final byte[] confJsAsBytes = confJsAsString.getBytes(StandardCharsets.UTF_8);
        final Path confJs = confsFolder.resolve("confs.js");
        if (confJs.toFile().exists()) {
            if (!confJs.toFile().delete()) {
                throw new IllegalStateException("Failed to delete conf.js");
            }
        }
        Files.write(confsFolder.resolve("confs.js"), confJsAsBytes, StandardOpenOption.CREATE);

        // Write mnemonics.js
        final String mnemonicJsAsString = "var mnemonicsMap = " + GSON.toJson(kanjiDataMap) + ";";
        final byte[] mnemonicJsAsBytes = mnemonicJsAsString.getBytes(StandardCharsets.UTF_8);
        final Path mnemonicsJs = confsFolder.resolve("mnemonics.js");
        if (mnemonicsJs.toFile().exists()) {
            if (!mnemonicsJs.toFile().delete()) {
                throw new IllegalStateException("Failed to delete mnemonics.js");
            }
        }
        Files.write(confsFolder.resolve("mnemonics.js"), mnemonicJsAsBytes, StandardOpenOption.CREATE);
    }

    private static void createConfDataMap(String[] kanjis) {
        for (String kanji : kanjis) {

            // Check if given kanji is actually known in the deck
            KanjiMnemonicData kmd = kanjiDataMap.get(asUnicode(kanji.charAt(0)));
            if (kmd == null) {
                throw new IllegalStateException("Failed to get KanjiData for kanji, need key: '" + kanji + "'");
            }

            KanjiConfData kcd = confDataMap.get(asUnicode(kanji.charAt(0)));
            if (kcd == null) {
                String concept = kmd.cp == null ? kmd.rtk : kmd.cp;
                String meta = kanji + " (" + kmd.r + ", " + concept.replaceAll("<br>.*", "") + ")";
                kcd = new KanjiConfData(kanji, meta, new LinkedHashSet<>(List.of(kanjis)), kmd.m);
                String key = asUnicode(kanji.charAt(0));
                confDataMap.put(key, kcd);
            } else {
                kcd.confs.addAll(List.of(kanjis));
            }
        }
    }

    // bad, creates too long chains: "泣", "涙", "漏", "泥", "戻", "房", "濡", "儒", "漬", "清", "積", "情", "浄", "精", "争", "静", "穏", "隠", "稲"
    private static void aggregateMissingConfs() {
        confDataMap.forEach((kanji, confData) -> {
            confData.confs.forEach(conf -> {
                KanjiConfData otherConfData = Optional.ofNullable(confDataMap.get(asUnicode(conf.charAt(0)))).orElseThrow();
                otherConfData.confs.addAll(confData.confs);
            });
        });
    }

// todo delete
//    private static void putIntoConfGroups(final String[] kanjis, final Integer groupId) {
//        // Get confGroup or create an empty one if not exists
//        final Map<String, KanjiMnemonicData> confGroup = confGroups.computeIfAbsent(groupId, k -> new HashMap<>());
//        ---
//        for (final String kanji : kanjis) {
//            // Check if given kanji is actually known in the deck
//            final KanjiMnemonicData kanjiData = kanjiDataMap.get(asUnicode(kanji.charAt(0)));
//            if (kanjiData == null) {
//                throw new IllegalStateException("Failed to get KanjiData for kanji, need key: '" + kanji + "'");
//            }
//            confGroup.put(asUnicode(kanji.charAt(0)), kanjiData);
//        }
//    }

    private static String[] extractKanji(final String line) {
        final String[] split = line.split(" cf ");
        for (final String kanji : split) {
            // Line has proper format
            if (!kanjiPattern.matcher(kanji).matches() || kanji.length() != 1) {
                throw new IllegalArgumentException("Failed to parse confList, need pattern '自 cf 白( cf 目)*', but line was: '" + line + "'");
            }
        }
        return split;
    }

    // Check if an ID exists for any for the kanji. If so, then it's a known group and every kanji in that line belongs to it.
    private static Integer getConfGroupId(final String[] kanjis) {
        Integer id = null;
        for (final String kanji : kanjis) {
            final Integer index = confGroupRefs.get(kanji);
            if (index != null) {
                id = index;
                break;
            }
        }
        if (id != null) {
            return id;
        }
        id = groupIndex.incrementAndGet();
        for (final String kanji : kanjis) {
            confGroupRefs.put(asUnicode(kanji.charAt(0)), id);
        }
        return id;
    }

    private static List<String> loadFileAsList(final Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to load file at: " + path);
        }
    }

    public static String asUnicode(final char character) {
        return Integer.toHexString(character);
    }


    private static class KanjiConfData {
        final String kj;
        final String meta;
        final Set<String> confs;
        final String mnemonic;

        public KanjiConfData(String kj, String meta, Set<String> confs, String mnemonic) {
            this.kj = kj;
            this.meta = meta;
            this.confs = confs;
            this.mnemonic = mnemonic;
        }
    }

}
