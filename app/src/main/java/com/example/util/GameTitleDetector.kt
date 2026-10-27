package com.example.util

import java.util.Locale

object GameTitleDetector {

    // Comprehensive mapping of common port project names, decompilations, and abbreviations to real commercial game titles
    private val KNOWN_ALIASES = mapOf(
        // Zelda ports & decompilations
        "lasrecomp" to "The Legend of Zelda: Link's Awakening",
        "las_recomp" to "The Legend of Zelda: Link's Awakening",
        "las-recomp" to "The Legend of Zelda: Link's Awakening",
        "linksawakening" to "The Legend of Zelda: Link's Awakening",
        "linkawakening" to "The Legend of Zelda: Link's Awakening",
        "linksa" to "The Legend of Zelda: Link's Awakening",
        "linka" to "The Legend of Zelda: Link's Awakening",
        "zeldalinkawakening" to "The Legend of Zelda: Link's Awakening",
        "zeldalinksawakening" to "The Legend of Zelda: Link's Awakening",
        "zelda64recomp" to "The Legend of Zelda: Majora's Mask",
        "z64recomp" to "The Legend of Zelda: Majora's Mask",
        "2ship" to "The Legend of Zelda: Majora's Mask",
        "2ship2harkinian" to "The Legend of Zelda: Majora's Mask",
        "shipwright" to "The Legend of Zelda: Ocarina of Time",
        "soh" to "The Legend of Zelda: Ocarina of Time",
        "shipofharkinian" to "The Legend of Zelda: Ocarina of Time",
        "zelda3" to "The Legend of Zelda: A Link to the Past",
        "zelda3android" to "The Legend of Zelda: A Link to the Past",
        "alttp" to "The Legend of Zelda: A Link to the Past",

        // Mario ports
        "sm64" to "Super Mario 64",
        "sm64port" to "Super Mario 64",
        "sm64android" to "Super Mario 64",
        "sm64ex" to "Super Mario 64",
        "sm64excoop" to "Super Mario 64",
        "sm64coopdeluxe" to "Super Mario 64",
        "sm64nx" to "Super Mario 64",
        "render96" to "Super Mario 64",
        "smw" to "Super Mario World",
        "smwandroid" to "Super Mario World",
        "galaxy" to "Super Mario Galaxy",
        "galaxyandroid" to "Super Mario Galaxy",
        "galaxy-android" to "Super Mario Galaxy",
        "galaxyport" to "Super Mario Galaxy",
        "smg" to "Super Mario Galaxy",
        "smgandroid" to "Super Mario Galaxy",
        "smg1" to "Super Mario Galaxy",
        "supermariogalaxy" to "Super Mario Galaxy",
        "galaxy2" to "Super Mario Galaxy 2",
        "galaxy2android" to "Super Mario Galaxy 2",
        "galaxy2-android" to "Super Mario Galaxy 2",
        "smg2" to "Super Mario Galaxy 2",
        "smg2android" to "Super Mario Galaxy 2",
        "supermariogalaxy2" to "Super Mario Galaxy 2",
        "sms" to "Super Mario Sunshine",
        "smsandroid" to "Super Mario Sunshine",
        "sunshineandroid" to "Super Mario Sunshine",
        "supermariosunshine" to "Super Mario Sunshine",
        "smo" to "Super Mario Odyssey",
        "smoandroid" to "Super Mario Odyssey",
        "supermarioodyssey" to "Super Mario Odyssey",
        "luigismansion" to "Luigi's Mansion",

        // Metroid
        "am2r" to "Another Metroid 2 Remake",
        "am2rlauncher" to "Another Metroid 2 Remake",

        // Diablo & Fallout
        "devilutionx" to "Diablo",
        "devilution" to "Diablo",
        "falloutce" to "Fallout",
        "fallout1ce" to "Fallout",
        "fallout2ce" to "Fallout 2",

        // Tomb Raider & Tycoons
        "openlara" to "Tomb Raider",
        "openrct2" to "RollerCoaster Tycoon 2",
        "corsixth" to "Theme Hospital",
        "corsix" to "Theme Hospital",
        "fheroes2" to "Heroes of Might and Magic II",
        "vcmi" to "Heroes of Might and Magic III",
        "ja2stracciatella" to "Jagged Alliance 2",
        "julius" to "Caesar III",
        "augustus" to "Caesar III",
        "oeraki" to "Pharaoh",
        "openttd" to "Transport Tycoon Deluxe",
        "simcity2000" to "SimCity 2000",
        "sc2k" to "SimCity 2000",

        // Cave Story & Indie
        "nxengine" to "Cave Story",
        "nxengineevo" to "Cave Story",
        "hollowknight" to "Hollow Knight",
        "hkandroid" to "Hollow Knight",
        "celeste" to "Celeste",
        "celesteandroid" to "Celeste",
        "cuphead" to "Cuphead",
        "deadcells" to "Dead Cells",
        "balatro" to "Balatro",
        "balatromobile" to "Balatro",
        "balatroapk" to "Balatro",
        "undertale" to "Undertale",
        "deltarune" to "Deltarune",
        "crosscode" to "CrossCode",
        "pizzatower" to "Pizza Tower",
        "rainworld" to "Rain World",
        "omori" to "Omori",
        "ddlc" to "Doki Doki Literature Club",
        "dokidoki" to "Doki Doki Literature Club",
        "shovelknight" to "Shovel Knight",
        "stardewvalley" to "Stardew Valley",
        "bloodbornepsx" to "Bloodborne PSX",

        // Grand Theft Auto
        "re3" to "Grand Theft Auto III",
        "re3android" to "Grand Theft Auto III",
        "revc" to "Grand Theft Auto: Vice City",
        "revcandroid" to "Grand Theft Auto: Vice City",
        "gtasa" to "Grand Theft Auto: San Andreas",

        // Elder Scrolls
        "openmw" to "The Elder Scrolls III: Morrowind",
        "openmwandroid" to "The Elder Scrolls III: Morrowind",
        "omw" to "The Elder Scrolls III: Morrowind",
        "daggerfallunity" to "The Elder Scrolls II: Daggerfall",

        // Valve / Half-Life / Quake / Doom
        "xash3d" to "Half-Life",
        "xash3dfwgs" to "Half-Life",
        "flyingwithgauntlets" to "Half-Life 2",
        "sourceengine" to "Half-Life 2",
        "srceng" to "Counter-Strike",
        "gzdoom" to "DOOM",
        "deltatouch" to "DOOM",
        "prboom" to "DOOM",
        "chocolatedoom" to "DOOM",
        "quadtouch" to "Quake",
        "vkquake" to "Quake",
        "darkplaces" to "Quake",
        "yquake2" to "Quake II",
        "yamagiquakeii" to "Quake II",
        "ioquake3" to "Quake III Arena",
        "dhewm3" to "DOOM 3",
        "eduke32" to "Duke Nukem 3D",
        "duke3d" to "Duke Nukem 3D",
        "raze" to "Duke Nukem 3D",
        "rtcw" to "Return to Castle Wolfenstein",
        "iortcw" to "Return to Castle Wolfenstein",
        "ecwolf" to "Wolfenstein 3D",

        // Sonic
        "sonicmania" to "Sonic Mania",
        "sonic3air" to "Sonic 3 A.I.R.",
        "sonic1" to "Sonic The Hedgehog",
        "sonic2" to "Sonic The Hedgehog 2",
        "soniccd" to "Sonic CD",

        // N64 / Banjo / Perfect Dark / Star Wars
        "perfectdark" to "Perfect Dark",
        "banjokazooie" to "Banjo-Kazooie",
        "bkrecomp" to "Banjo-Kazooie",
        "kotor" to "Star Wars: Knights of the Old Republic",
        "openjk" to "Star Wars Jedi Knight: Jedi Academy",
        "jediacademy" to "Star Wars Jedi Knight: Jedi Academy",
        "darkforces" to "Star Wars: Dark Forces",
        "forceengine" to "Star Wars: Dark Forces",

        // Classic PC & Arcade
        "spacecadetpinball" to "3D Pinball: Space Cadet",
        "spacecadet" to "3D Pinball: Space Cadet",
        "jazz2" to "Jazz Jackrabbit 2",
        "commanderkeen" to "Commander Keen",
        "abuse" to "Abuse",
        "wipeout" to "Wipeout",
        "wipeoutrewrite" to "Wipeout",
        "serioussam" to "Serious Sam",
        "shadowwarrior" to "Shadow Warrior",
        "blood" to "Blood",
        "descent" to "Descent",
        "d1xrebirth" to "Descent",
        "d2xrebirth" to "Descent II",
        "marathon" to "Marathon",
        "alephone" to "Marathon",
        "systemshock" to "System Shock",
        "thief" to "Thief: The Dark Project",
        "deusex" to "Deus Ex",
        "outlaws" to "Outlaws",
        "heretic" to "Heretic",
        "hexen" to "Hexen",
        "strife" to "Strife",
        "chexquest" to "Chex Quest",
        "supertuxkart" to "SuperTuxKart",
        "supertux" to "SuperTux",
        "mindustry" to "Mindustry",
        "endlesssky" to "Endless Sky",
        "cdda" to "Cataclysm: Dark Days Ahead",
        "shatteredpixeldungeon" to "Shattered Pixel Dungeon",
        "unreal" to "Unreal",
        "ut99" to "Unreal Tournament"
    )

    /**
     * Resolves the authentic commercial game title from a repository name, description,
     * or user input.
     */
    fun detectRealTitle(repoOrProjectName: String, description: String = "", releaseTitle: String = ""): String {
        val cleanName = repoOrProjectName.trim()
        val normalized = cleanName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

        // 1. Direct exact dictionary match
        KNOWN_ALIASES[normalized]?.let { return it }

        // 2. Substring dictionary match in normalized string
        for ((key, title) in KNOWN_ALIASES) {
            if (key.length >= 4 && (normalized.contains(key) || key.contains(normalized))) {
                return title
            }
        }

        // 3. Search in description or releaseTitle for specific well-known titles
        val combinedText = "$description $releaseTitle"
        val wellKnownFranchises = listOf(
            "Link's Awakening" to "The Legend of Zelda: Link's Awakening",
            "Link Awakening" to "The Legend of Zelda: Link's Awakening",
            "Link's A" to "The Legend of Zelda: Link's Awakening",
            "Links A" to "The Legend of Zelda: Link's Awakening",
            "Link A" to "The Legend of Zelda: Link's Awakening",
            "Ocarina of Time" to "The Legend of Zelda: Ocarina of Time",
            "Majora's Mask" to "The Legend of Zelda: Majora's Mask",
            "Majora Mask" to "The Legend of Zelda: Majora's Mask",
            "A Link to the Past" to "The Legend of Zelda: A Link to the Past",
            "The Legend of Zelda" to "The Legend of Zelda",
            "Super Mario 64" to "Super Mario 64",
            "Super Mario World" to "Super Mario World",
            "Super Mario Galaxy 2" to "Super Mario Galaxy 2",
            "Super Mario Galaxy" to "Super Mario Galaxy",
            "Galaxy-Android" to "Super Mario Galaxy",
            "Galaxy Android" to "Super Mario Galaxy",
            "Galaxy" to "Super Mario Galaxy",
            "Hollow Knight" to "Hollow Knight",
            "Celeste" to "Celeste",
            "Balatro" to "Balatro",
            "Cuphead" to "Cuphead",
            "Dead Cells" to "Dead Cells",
            "Grand Theft Auto" to "Grand Theft Auto",
            "GTA III" to "Grand Theft Auto III",
            "GTA Vice City" to "Grand Theft Auto: Vice City",
            "GTA San Andreas" to "Grand Theft Auto: San Andreas",
            "RollerCoaster Tycoon" to "RollerCoaster Tycoon 2",
            "Theme Hospital" to "Theme Hospital",
            "Heroes of Might and Magic" to "Heroes of Might and Magic II",
            "Tomb Raider" to "Tomb Raider",
            "Sonic Mania" to "Sonic Mania",
            "Fallout 2" to "Fallout 2",
            "Fallout" to "Fallout",
            "Diablo" to "Diablo",
            "Morrowind" to "The Elder Scrolls III: Morrowind",
            "Half-Life" to "Half-Life",
            "Wolfenstein" to "Wolfenstein 3D",
            "Cave Story" to "Cave Story",
            "Doki Doki Literature Club" to "Doki Doki Literature Club",
            "Pizza Tower" to "Pizza Tower",
            "Rain World" to "Rain World",
            "Omori" to "Omori",
            "Star Wars: Knights of the Old Republic" to "Star Wars: Knights of the Old Republic",
            "Star Wars Jedi Knight" to "Star Wars Jedi Knight: Jedi Academy",
            "Space Cadet Pinball" to "3D Pinball: Space Cadet",
            "SimCity 2000" to "SimCity 2000"
        )

        for ((phrase, officialTitle) in wellKnownFranchises) {
            if (combinedText.contains(phrase, ignoreCase = true) || cleanName.contains(phrase, ignoreCase = true)) {
                return officialTitle
            }
        }

        // 4. Regex pattern extraction from description
        // e.g. "port of [Game Name] for Android" or "recompilation of [Game Name]"
        val regexPatterns = listOf(
            Regex("""(?i)(?:port|decompilation|recompilation|reimplementation|source\s+port)\s+(?:of|for)\s+([A-Za-z0-9: '\-–]+?)(?:\s+(?:for|on|to|in)\s+android|\s+decompilation|\s+source\s+port|[.,;!\n\r]|$)"""),
            Regex("""(?i)Android\s+port\s+of\s+([A-Za-z0-9: '\-–]+?)(?:\s+decompilation|[.,;!\n\r]|$)""")
        )

        for (pattern in regexPatterns) {
            val match = pattern.find(description)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length in 3..40 && !candidate.equals("the", ignoreCase = true)) {
                    return candidate
                }
            }
        }

        // 5. Fallback: Clean up the repository or project name
        return cleanName
            .replace("-android", "", ignoreCase = true)
            .replace("_android", "", ignoreCase = true)
            .replace("-port", "", ignoreCase = true)
            .replace("_port", "", ignoreCase = true)
            .replace("-recomp", "", ignoreCase = true)
            .replace("_recomp", "", ignoreCase = true)
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}
