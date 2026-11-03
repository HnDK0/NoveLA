import { Book, Chapter, SourceExtension } from '../types';

export const BUILT_IN_EXTENSIONS: SourceExtension[] = [
  {
    id: 'royalroad',
    name: 'Royal Road',
    version: '1.4.2',
    language: 'EN',
    baseUrl: 'https://www.royalroad.com',
    icon: '👑',
    contentType: 'NOVEL',
    isInstalled: true,
    isBuiltIn: true,
    description: 'Premier home of web serial fiction, LitRPG, progression fantasy, and sci-fi.',
  },
  {
    id: 'wuxiaworld',
    name: 'Wuxiaworld',
    version: '2.1.0',
    language: 'EN',
    baseUrl: 'https://www.wuxiaworld.com',
    icon: '⚔️',
    contentType: 'NOVEL',
    isInstalled: true,
    isBuiltIn: true,
    description: 'Chinese and Korean fantasy web novel translations, cultivation, and martial arts.',
  },
  {
    id: 'scribblehub',
    name: 'Scribble Hub',
    version: '1.2.0',
    language: 'EN',
    baseUrl: 'https://www.scribblehub.com',
    icon: '🖋️',
    contentType: 'NOVEL',
    isInstalled: true,
    isBuiltIn: true,
    description: 'Original web novels with romance, fantasy, reincarnation, and litRPG tags.',
  },
  {
    id: 'ranobes',
    name: 'Ranobes',
    version: '1.1.8',
    language: 'EN',
    baseUrl: 'https://ranobes.top',
    icon: '📖',
    contentType: 'NOVEL',
    isInstalled: true,
    isBuiltIn: true,
    description: 'Light novels, web novels, and Asian fiction translations with large chapter archive.',
  },
  {
    id: 'novelfull',
    name: 'NovelFull',
    version: '1.0.9',
    language: 'EN',
    baseUrl: 'https://novelfull.net',
    icon: '⚡',
    contentType: 'NOVEL',
    isInstalled: true,
    isBuiltIn: true,
    description: 'Massive archive of translated Asian web novels across all major genres.',
  },
  {
    id: 'mtlnovel',
    name: 'MTLNovel',
    version: '1.3.1',
    language: 'EN',
    baseUrl: 'https://www.mtlnovel.com',
    icon: '🤖',
    contentType: 'NOVEL',
    isInstalled: true,
    isBuiltIn: true,
    description: 'Rapid machine-translated raw web novels from Chinese, Korean, and Japanese.',
  },
  {
    id: 'mangadex',
    name: 'MangaDex (Comics)',
    version: '1.5.0',
    language: 'EN',
    baseUrl: 'https://mangadex.org',
    icon: '🎨',
    contentType: 'MANGA',
    isInstalled: true,
    isBuiltIn: true,
    description: 'Manga and webcomic reader support with horizontal and vertical webtoon viewer.',
  },
  {
    id: 'localsource',
    name: 'Local Books',
    version: '1.0.0',
    language: 'ALL',
    baseUrl: 'local://',
    icon: '💾',
    contentType: 'NOVEL',
    isInstalled: true,
    isBuiltIn: true,
    description: 'Imported local EPUB files, FB2 files, and manual text novels.',
  },
];

// Rich mock catalog of books with authentic multi-chapter content
export const INITIAL_BOOKS: Book[] = [
  {
    title: 'Defiance of the Fall',
    url: 'royalroad:defiance-of-the-fall',
    author: 'JF Brink (TheFirstDefier)',
    completed: false,
    inLibrary: true,
    coverImageUrl: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=600&q=80',
    description: 'Zac was just a normal guy camping in the woods when Earth was integrated into the Multiverse. With civilization instantly collapsed into chaotic dimensional incursions, mutated beasts, and demonic invaders, he finds himself trapped on a hostile alien island with only a broken lumberjack axe and his wits. Forced to adapt to System stats, cosmic Dao mastery, and monstrous cosmic incursions, Zac embarks on a relentless path of bloody defiance.',
    lastReadEpochTimeMilli: Date.now() - 3600000 * 2,
    addedToLibraryEpochTimeMilli: Date.now() - 86400000 * 5,
    lastUpdateEpochTimeMilli: Date.now() - 3600000 * 12,
    lastReadChapter: 'royalroad:defiance-of-the-fall:c1',
    category: 'Reading',
    genres: 'LitRPG,Progression Fantasy,Action,Adventure,Sci-Fi',
    rating: '4.8',
    status: 'Ongoing',
    lastUpdateDate: 'Today',
    contentType: 'NOVEL',
    sourceId: 'royalroad',
    sourceName: 'Royal Road',
    totalChapters: 6,
  },
  {
    title: 'Coiling Dragon',
    url: 'wuxiaworld:coiling-dragon',
    author: 'I Eat Tomatoes (Wo Chi Xi Hong Shi)',
    completed: true,
    inLibrary: true,
    coverImageUrl: 'https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=600&q=80',
    description: 'Empires rise and fall on the Yulan Continent. Saints and godly immortals dominate the skies with unimaginable magical beasts. Linley Baruch is a young scion of the once-glorious Dragonblood Warrior clan whose clan heritage has dwindled into obscurity. One day, Linley stumbles upon the Coiling Dragon Ring. Guided by the ancient spirit Doehring Cowart, Linley steps onto the peak of warrior and magus cultivation.',
    lastReadEpochTimeMilli: Date.now() - 86400000 * 2,
    addedToLibraryEpochTimeMilli: Date.now() - 86400000 * 14,
    lastUpdateEpochTimeMilli: Date.now() - 86400000 * 30,
    lastReadChapter: 'wuxiaworld:coiling-dragon:c2',
    category: 'Completed',
    genres: 'Xianxia,Martial Arts,Fantasy,Action,Reincarnation',
    rating: '4.9',
    status: 'Completed',
    lastUpdateDate: '2 weeks ago',
    contentType: 'NOVEL',
    sourceId: 'wuxiaworld',
    sourceName: 'Wuxiaworld',
    totalChapters: 5,
  },
  {
    title: 'The Primal Hunter',
    url: 'royalroad:the-primal-hunter',
    author: 'Zogarth',
    completed: false,
    inLibrary: true,
    coverImageUrl: 'https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?auto=format&fit=crop&w=600&q=80',
    description: 'A hunter thrives on the thrill of the chase. Jake was an ordinary office worker living in a monotonous routine until humanity was torn from Earth and plunged into the Tutorial of the System. While others panicked, Jake found his senses awakening, his instinct yearning for the thrill of archery, alchemy, and solitary combat against towering monsters.',
    lastReadEpochTimeMilli: Date.now() - 86400000 * 1,
    addedToLibraryEpochTimeMilli: Date.now() - 86400000 * 7,
    lastUpdateEpochTimeMilli: Date.now() - 3600000 * 6,
    lastReadChapter: 'royalroad:the-primal-hunter:c1',
    category: 'Reading',
    genres: 'LitRPG,Action,Adventure,Fantasy,Magic',
    rating: '4.7',
    status: 'Ongoing',
    lastUpdateDate: 'Yesterday',
    contentType: 'NOVEL',
    sourceId: 'royalroad',
    sourceName: 'Royal Road',
    totalChapters: 5,
  },
  {
    title: 'Shadow Slave',
    url: 'novelfull:shadow-slave',
    author: 'Guiltythree',
    completed: false,
    inLibrary: false,
    coverImageUrl: 'https://images.unsplash.com/photo-1514533450685-4493e01d1fdc?auto=format&fit=crop&w=600&q=80',
    description: 'Growing up in poverty in the outskirts, Sunny never expected anything good from life. When the Nightmare Spell chose him at age sixteen, he was transported to the brutal Forgotten Shore. In this ruined mythological realm filled with terrifying abominations, Sunny received a miraculous yet treacherous Divine Aspect: the ability to command shadows and manipulate darkness, bound by a deadly flaw.',
    lastReadEpochTimeMilli: 0,
    addedToLibraryEpochTimeMilli: 0,
    lastUpdateEpochTimeMilli: Date.now() - 3600000 * 4,
    category: 'Plan to Read',
    genres: 'Dark Fantasy,Mystery,Supernatural,LitRPG,Survival',
    rating: '4.9',
    status: 'Ongoing',
    lastUpdateDate: '3 hours ago',
    contentType: 'NOVEL',
    sourceId: 'novelfull',
    sourceName: 'NovelFull',
    totalChapters: 6,
  },
  {
    title: 'Lord of the Mysteries',
    url: 'ranobes:lord-of-the-mysteries',
    author: 'Cuttlefish That Loves Diving',
    completed: true,
    inLibrary: false,
    coverImageUrl: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=600&q=80',
    description: 'With the rising tide of steam power and machinery, who can come close to being an extraordinary Beyonder? Klein Moretti awakens in a Victorian-esque world of steam, airships, alchemy, and terrifying Tarot divination. As he uncovers esoteric sequence potions and forgotten deities, he adopts the moniker "The Fool" and convenes the Tarot Club.',
    lastReadEpochTimeMilli: 0,
    addedToLibraryEpochTimeMilli: 0,
    lastUpdateEpochTimeMilli: Date.now() - 86400000 * 60,
    category: '',
    genres: 'Steampunk,Mystery,Occult,Lovecraftian,Historical',
    rating: '5.0',
    status: 'Completed',
    lastUpdateDate: '1 month ago',
    contentType: 'NOVEL',
    sourceId: 'ranobes',
    sourceName: 'Ranobes',
    totalChapters: 6,
  },
  {
    title: 'Chrysalis',
    url: 'scribblehub:chrysalis',
    author: 'RinoZ',
    completed: false,
    inLibrary: false,
    coverImageUrl: 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80',
    description: 'Anthony was a simple young man who lived for his family and pet ants. After an untimely death, he wakes up reincarnated inside the deepest depths of the Great Pangera Dungeon as an ant larva! Armed with a human mind, relentless hunger for biomass, and devotion to the Colony, Anthony will evolve his ant body into an apex predator of the abyss.',
    lastReadEpochTimeMilli: 0,
    addedToLibraryEpochTimeMilli: 0,
    lastUpdateEpochTimeMilli: Date.now() - 86400000 * 4,
    category: '',
    genres: 'Reincarnation,Monster Evolution,Comedy,LitRPG,Action',
    rating: '4.6',
    status: 'Ongoing',
    lastUpdateDate: '4 days ago',
    contentType: 'NOVEL',
    sourceId: 'scribblehub',
    sourceName: 'Scribble Hub',
    totalChapters: 5,
  },
  {
    title: 'Solo Leveling (Comic)',
    url: 'mangadex:solo-leveling',
    author: 'Chugong & DUBU (REDICE)',
    completed: true,
    inLibrary: false,
    coverImageUrl: 'https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?auto=format&fit=crop&w=600&q=80',
    description: 'Ten years ago, the "Gate" connected our world with the monster-filled underworld. Sung Jin-woo is the world\'s weakest E-rank Hunter. Trapped in a terrifying double dungeon during a raid, he accepts a mysterious quest system in his dying moments, granting him the unique ability to level up infinitely.',
    lastReadEpochTimeMilli: 0,
    addedToLibraryEpochTimeMilli: 0,
    lastUpdateEpochTimeMilli: Date.now() - 86400000 * 180,
    category: '',
    genres: 'Action,Supernatural,Dungeon,Webtoon,Fantasy',
    rating: '4.9',
    status: 'Completed',
    lastUpdateDate: 'Completed',
    contentType: 'MANGA',
    sourceId: 'mangadex',
    sourceName: 'MangaDex (Comics)',
    totalChapters: 4,
  },
];

// Rich chapters sample with authentic paragraph splits
export const INITIAL_CHAPTERS: Record<string, Chapter[]> = {
  'royalroad:defiance-of-the-fall': [
    {
      title: 'Chapter 1: The Integration',
      url: 'royalroad:defiance-of-the-fall:c1',
      bookUrl: 'royalroad:defiance-of-the-fall',
      position: 1,
      read: true,
      lastReadPosition: 3,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 100,
      volume: 'Volume 1',
      isDownloaded: true,
      content: `The wind howled between the towering pines of the Pacific Northwest, carrying the scent of damp moss and impending storm.

Zac leaned against his wooden deck railing, holding a steaming mug of black coffee. He had escaped to this secluded campsite to clear his head from the endless drone of city life. The silence was therapeutic, punctuated only by the occasional cry of an eagle circling the grey canopy above.

Then, without any preamble, the sky shattered.

It wasn't a metaphor. The pale blue dome of the atmosphere fractured like brittle crystal, revealing iridescent violet veins that bled raw, kaleidoscopic light into the clouds. A deafening chime vibrated directly inside Zac's skull, resonant like a bronze temple bell struck by a comet.

[Planetary Integration Initiated.]
[World designation: Earth (Sector 114).]
[Cosmic Energy Convergence rate: 100%.]
[Welcome to the System, mortal. Survive or become fertilizer for the Dao.]

Zac stumbled backward, splashing scorching coffee over his wrist, but he barely felt the burn. His eyes widened as translucent cyan letters coalesced in his field of vision.

"What... what kind of hallucination is this?" he muttered, his breath coming in shallow gasps.

Before he could steady his footing, a shockwave of crystalline gravity crushed him to the wooden planks. The Earth beneath him groaned as tectonic plates wrenched and expanded. Mountain peaks kilometers away erupted with pillars of purple lightning, and the gentle woodland forest around him twisted into jagged, alien foliage.

In the corner of his eye, a small status notification pulsed:

Name: Zachary Atwood
Level: 0 (Mortal)
Race: Human (Earth)
Class: None
Strength: 10
Dexterity: 9
Endurance: 11
Vitality: 10
Wisdom: 8
Intelligence: 8
Free Attribute Points: 0

A rustle erupted from the tree line fifty feet away. From the newly warped brambles stepped a creature out of nightmare—a canine beast the size of a black bear, covered in spiky chitin plates, its crimson eyes locked directly onto Zac's throat.

Zac’s hand instinctively reached behind him, fingers wrapping around the weathered handle of his lumberjack splitting axe.

The old world was gone. The hunt had begun.`,
    },
    {
      title: 'Chapter 2: First Blood',
      url: 'royalroad:defiance-of-the-fall:c2',
      bookUrl: 'royalroad:defiance-of-the-fall',
      position: 2,
      read: false,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 99,
      volume: 'Volume 1',
      isDownloaded: true,
      content: `The armored hound let out a low, vibrating growl that shook the dry leaves under Zac's boots. Saliva dripped from serrated ivory fangs, sizzling as it struck the damp soil.

Zac didn't wait. Fear spiked his adrenaline, sharpening his perception until the rain droplets suspended in the breeze seemed to slow to a crawl. He lowered his center of gravity, gripping the axe with both hands.

The beast leaped. It cleared thirty feet in a single bound, claws outstretched to pin him to the earth.

Zac sidestepped hard to the left, pivoting on his back foot. The hound slammed into the wooden porch railing behind him, splintering redwood timber with the force of a battering ram. As it twisted to regain its balance, Zac swung the heavy steel wedge with every ounce of strength in his core.

CLANG!

The axe blade struck the chitinous armor on the beast's flank, throwing up sparks. A sickening crack echoed through the glade as the armor fractured, purple hemolymph spurting across Zac's jacket.

The beast snarled in agony and whipped its barbed tail. The blow caught Zac across his ribs, launching him into the dirt and knocking the air from his lungs. Pain flared white-hot along his side.

[Warning: Health at 65%. Minor rib fracture detected.]

"Shut up..." Zac wheezed, rolling to his stomach as the beast lunged again.

With a roar born of desperation, Zac thrust the poll of the axe directly into the beast's open maw. The heavy iron wedged between its jaws, snapping teeth. Zac followed through with his body weight, driving his combat knife into the exposed neck joint with savage fury.

The beast shuddered. Its crimson eyes dimmed, and its massive body collapsed like a falling boulder.

[Enemy slain: Level 3 Spined Hound.]
[Experience awarded: 120 XP.]
[Level up! You are now Level 1.]
[+5 Free Attribute Points acquired.]

Zac slumped against the dead beast, his chest heaving. Looking up at the fractured violet sky, he realized one undeniable truth: he was going to need a much bigger axe.`,
    },
    {
      title: 'Chapter 3: The Tree of the Dao',
      url: 'royalroad:defiance-of-the-fall:c3',
      bookUrl: 'royalroad:defiance-of-the-fall',
      position: 3,
      read: false,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 98,
      volume: 'Volume 1',
      content: `Three days had passed since the integration. The campsite had become a fortified bastion of sharp stakes and traps.

Zac had allocated his five attribute points into Strength and Vitality. The change was immediately palpable: his muscles felt denser, his endurance surged, and the fractured rib had knit together overnight with terrifying speed.

While scouting the perimeter near the granite falls, Zac discovered something extraordinary. In the center of a newly created crater stood a sapphire sapling glowing with crystalline luminescence. Its roots reached deep into the bedrock, sipping pure ambient Cosmic Energy from the atmosphere.

As Zac approached, a warm hum reverberated through his dantian.

[Discovery: Primordial Dao Seedling (Heavens Cleaver lineage).]
[Affinity match: 94%.]
[Do you wish to meditate upon the Dao of Heavens Severance?]

Zac sat cross-legged upon the granite slab, closing his eyes. The universe suddenly expanded before his mental sight into infinite golden threads of force and movement.`,
    },
    {
      title: 'Chapter 4: Incursion Beacon',
      url: 'royalroad:defiance-of-the-fall:c4',
      bookUrl: 'royalroad:defiance-of-the-fall',
      position: 4,
      read: false,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 95,
      volume: 'Volume 1',
      content: `A crimson beam pierced the night sky three miles to the south.

The System notified all survivors in Sector 114:
[Event: Incursion Beacon Active.]
[Demonic Vanguard Outpost establishing in 48 hours.]
[Destroy the Beacon or the island will be consumed by Netherfire.]

Zac strapped his upgraded Hatchet of the Wilds to his belt. He had brewed ten vials of beast blood tonic and harvested twenty ironwood arrows.

"Time to pay the demons a visit," he said coldly.`,
    },
    {
      title: 'Chapter 5: Gathering the Remnants',
      url: 'royalroad:defiance-of-the-fall:c5',
      bookUrl: 'royalroad:defiance-of-the-fall',
      position: 5,
      read: false,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 90,
      volume: 'Volume 1',
      content: `Smoke rose from the valley below. Through his enhanced optical visor, Zac spotted a perimeter of crude barbed wire where seven human survivors were desperately holding off a pack of level 2 scale-rats.

Among them was a woman channeling silver luminescence from a carved wooden staff—a healer class.

Zac leaped from the twenty-foot bluff, his axe whistling through the humid air like a thunderclap.`,
    },
    {
      title: 'Chapter 6: Lord of Portals',
      url: 'royalroad:defiance-of-the-fall:c6',
      bookUrl: 'royalroad:defiance-of-the-fall',
      position: 6,
      read: false,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 85,
      volume: 'Volume 1',
      content: `The demonic commander towered eight feet high, clad in blackened obsidian plate. Twin horns curved backward over a skull bathed in violet hellfire.

"Mortal scum," the creature hissed in fluent Imperial Galactic tongue. "You dare impede the vanguard of the Great Void?"

Zac spat blood onto the obsidian cobbles and grinned. "I'm just a guy chopping down weeds."`,
    },
  ],
  'wuxiaworld:coiling-dragon': [
    {
      title: 'Chapter 1: The Dragonblood Clan',
      url: 'wuxiaworld:coiling-dragon:c1',
      bookUrl: 'wuxiaworld:coiling-dragon',
      position: 1,
      read: true,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 300,
      volume: 'Book 1: Early Morning Awakening',
      content: `The township of Wushan was nestled quietly against the foot of Mount Wushan. Early morning mist wrapped the thatched cottages in a dreamlike shroud.

On an open training ground at the eastern edge of the village, dozens of young boys stood in rigid formation, their fists thrust forward in unison as sweat dripped from their youthful faces.

"Hold your breath! Keep your waist firm and your shoulders squared!" shouted Hillman, the fierce-faced warrior instructor whose broadsword rested casually against an ancient stone pillar. "The path of a warrior requires the unyielding resolve of granite!"

In the front row, an eight-year-old boy named Linley Baruch held his stance without blinking. His dark brown hair fluttered in the autumn breeze, and his gaze was fixed steadily forward.

Linley was not like the other village children. He was the eldest son of Hogg Baruch, the clan leader of the legendary Dragonblood Warriors. Five thousand years ago, the Supreme Dragonblood Warriors had stood at the pinnacle of the Yulan continent, battling Saint-rank magical beasts with bare hands.

Yet now, the Baruch clan manor lay half in ruins, its treasures sold off to pay imperial taxes.

"Someday," Linley whispered into the cold mountain air, "I will restore the honor of my ancestors."`,
    },
    {
      title: 'Chapter 2: The Coiling Dragon Ring',
      url: 'wuxiaworld:coiling-dragon:c2',
      bookUrl: 'wuxiaworld:coiling-dragon',
      position: 2,
      read: true,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 299,
      volume: 'Book 1: Early Morning Awakening',
      content: `While exploring the dilapidated back courtyard of his family estate, Linley fell through a collapsed cellar roof into a hidden chamber shrouded in centuries of dust.

As he brushed rubble off an ancient stone altar, a small, unremarkable ring caught his attention. It appeared to be carved of plain black stone, shaped like a miniature dragon coiling onto itself.

A stray jagged nail nicked Linley's index finger. A drop of red blood dripped directly onto the dragon head of the ring.

Instantly, the chamber filled with a soft azure radiance!

The ring floated from the stone slab, dissolving into translucent spiritual mist that encircled Linley's chest before seeping directly into his flesh. An ethereal elder with long white hair and majestic cyan robes appeared floating in mid-air before the stunned child.

"Hahaha! After five thousand years trapped within the Coiling Dragon Ring, someone has finally awakened me with the blood of a Dragonblood Warrior!"

Linley tumbled backward, eyes wide. "Who... who are you?"

The old man stroked his long beard, eyes sparkling with benevolence. "Young man, my name is Doehring Cowart. In my former life, I was a Grand Magus of the Saint realm!"`,
    },
    {
      title: 'Chapter 3: Dual Element Affinity',
      url: 'wuxiaworld:coiling-dragon:c3',
      bookUrl: 'wuxiaworld:coiling-dragon',
      position: 3,
      read: false,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 295,
      volume: 'Book 1: Early Morning Awakening',
      content: `Under Doehring Cowart's guidance, Linley placed his palm upon the crystal appraisal orb.

Normally, humans only possess faint spiritual affinity for a single element. But when Linley closed his eyes, the crystal sphere erupted with blinding dual colors—an emerald swirl of Earth essence accompanied by a raging spiral of Wind essence!

"Exceptional! Exceptional spiritual talent!" the old Saint exclaimed. "You have the capacity to become a dual-element Saint Magus!"`,
    },
    {
      title: 'Chapter 4: The Ernst Institute',
      url: 'wuxiaworld:coiling-dragon:c4',
      bookUrl: 'wuxiaworld:coiling-dragon',
      position: 4,
      read: false,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 290,
      volume: 'Book 2: Ernst Institute',
      content: `The Holy Capital of Fenlai was the most magnificent metropolis in the Holy Union. In its northern outskirts stood the prestigious Ernst Institute, the number one magical academy on the entire continent.

Linley arrived accompanied by his little Shadow Mouse companion, Bebe, tucked snuggly inside his pocket.`,
    },
    {
      title: 'Chapter 5: The Mountain of Magical Beasts',
      url: 'wuxiaworld:coiling-dragon:c5',
      bookUrl: 'wuxiaworld:coiling-dragon',
      position: 5,
      read: false,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 280,
      volume: 'Book 3: Mountain of Magical Beasts',
      content: `Linley drew his Bloodviolet blade as the dense foliage of the mountain parted. A rank-7 Bloodthirsty Armored Bull charged forward like a runaway siege engine.

"Windblade spell: Sonic Decapitation!" Linley chanted, his silhouette blurring into thirty wind afterimages.`,
    },
  ],
  'mangadex:solo-leveling': [
    {
      title: 'Chapter 1: The Weakest Hunter',
      url: 'mangadex:solo-leveling:c1',
      bookUrl: 'mangadex:solo-leveling',
      position: 1,
      read: false,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 120,
      volume: 'Prologue',
      images: [
        'https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=900&q=80',
        'https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=900&q=80',
        'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=900&q=80',
        'https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?auto=format&fit=crop&w=900&q=80',
      ],
      content: `[Visual Comic Chapter - Switch to Comic/Manga Reader view]
Page 1: The dual dungeon entrance looms deep beneath the subway tunnels.
Page 2: Sung Jin-woo clenches his rusted dagger, trembling before the colossal stone statues.
Page 3: The giant stone god smiles a terrifying, frozen grin as blue flames ignite along the altar.
Page 4: "Run! The doors are sealing!"`,
    },
    {
      title: 'Chapter 2: The Double Dungeon',
      url: 'mangadex:solo-leveling:c2',
      bookUrl: 'mangadex:solo-leveling',
      position: 2,
      read: false,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now() - 86400000 * 115,
      volume: 'Double Dungeon Arc',
      images: [
        'https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?auto=format&fit=crop&w=900&q=80',
        'https://images.unsplash.com/photo-1514533450685-4493e01d1fdc?auto=format&fit=crop&w=900&q=80',
      ],
    },
  ],
};
