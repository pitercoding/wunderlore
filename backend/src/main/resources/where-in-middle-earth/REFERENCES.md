# Where in Middle-earth — sources and verification record

Scope: the 45 places (15 EASY, 15 MEDIUM, 15 HARD) in `locations.json`, and how each was placed on the
original map (`contracts/where-in-middle-earth/map-geometry.json`, drawn by the frontend).

## How to read this record

All sources are **Tolkien Gateway** pages (a secondary, community-written encyclopedia). The primary works
(The Hobbit, The Lord of the Rings and its Appendices, The Silmarillion) were **not** consulted directly, and no
page or chapter numbers are claimed. Where a Tolkien Gateway page cites a chapter, that citation is the wiki's,
not something confirmed against the books here. Wikipedia and the Encyclopedia of Arda were not used.

On **2026-11-07** each page listed below was retrieved from Tolkien Gateway (its public MediaWiki API, plain-text
extracts, with an identifying User-Agent; every page answered HTTP 200 and no access restriction was bypassed)
and read for the geographic statements the entry relies on. "Fact supported" gives the substance of what the page
says, in my own words; the explanation shown in the game is worded to stay within it.

Every source is **secondary**. No fact in this record was confirmed against a primary source.

## What the map is, and what it is not

- The map is an **original, schematic** drawing made for Wunderlore. It was not traced or derived from any
  published map (books, films or websites). Only the *relations* the pages state were used: which place is
  north, south, east or west of which, and the few distances they give.
- It is **not to scale**. Positions keep the stated directions and the rough ordering, with local distances
  exaggerated so that neighbouring places can be told apart and scored fairly. The distances in miles that the
  pages give (about forty miles from the Shire to Bree, about 100 miles from Bree to Fornost, about 30 miles from
  Mount Doom to Barad-dûr...) are therefore *not* reproduced proportionally.
- Every coordinate is an **editorial placement**: it is as accurate as the page's wording allows (the column
  "Placement" says which relation fixed it), and no more. The scoring bands (see
  `contracts/where-in-middle-earth/README.md`) absorb that imprecision: the best band is a 15-unit radius and the
  widest is 90 units, on a 1000 x 700 map.
- The map shows one geography, Third-Age Middle-earth (Eriador, the Misty Mountains, Wilderland, Rohan, Gondor
  and Mordor). The `period` field of each place says when it existed, and is shown with the explanation.

## Ages

- **Second Age:** `ost-in-edhil` only. Its site, Eregion (Hollin), is on the map; the city itself stood until the
  fall of Eregion in S.A. 1697 (the Rivendell page dates Rivendell's founding to that event).
- **Third Age, fallen or ruined by the War of the Ring:** `fornost`, `carn-dum`, `annuminas`, `tharbad`.
  `minas-morgul` was Minas Ithil until T.A. 2002.
- Every other place is a Third-Age place that stood in the time of The Hobbit or The Lord of the Rings.

## Editorial decisions

- **First Age places left out.** The proposed Hard list had Nargothrond, Gondolin, Doriath, Menegroth,
  Tol-in-Gaurhoth and Amon Ereb. They lay in Beleriand, which is not part of the Third-Age geography the map
  shows: placing them on it would draw First-Age lands over the Third-Age sea. They were replaced by Third-Age
  places in Eriador, Gondor and Mordor.
- **Region-sized places left out.** The Shire, Rohan, Mordor and Angmar are regions, so a single point would be
  an arbitrary target. Places inside them were used instead (Hobbiton, Edoras, Mount Doom, Carn Dûm). The
  regions appear on the map only as names.
- **Duplicates left out.** Amon Sûl is Weathertop; Azanulbizar is Dimrill Dale; Eregion contains Ost-in-Edhil;
  the Paths of the Dead open beside Dunharrow. Each is asked once.
- **Moved between difficulties.** Weathertop and Erebor were moved to Easy (well known).
- **Added.** Easy: The Black Gate. Medium: The Barrow-downs, Dol Amroth, Emyn Muil, The Iron Hills (replacing
  Weathertop, Erebor, Caras Galadhon, which is in the same forest as Lothlórien, and the Paths of the Dead).
  Hard: Mount Gundabad, Beorn's Hall, The Fords of Isen, Cair Andros, The Isenmouthe, Umbar, Edhellond, Henneth
  Annûn, The Old Forest.
- **Dagorlad not used.** Its page is one sentence (a plain north-west of Mordor, below the Black Gate), not enough
  to fix a point.

## Placement on the map

The map is 1000 x 700 (x east, y south). The land outline, nine broad regions and the list of labels written on
the map are in `contracts/where-in-middle-earth/map-geometry.json`. The backend test checks that every place is
on land, inside its region, at least 4 units from the shore, and that no label on the map is the name of a place.

## Places

Source type for every row: **secondary** (Tolkien Gateway page). "Placement" is the relation used to put the point
where it is; "Caveats" lists what the page does not settle.


### Easy

| ID | Place | Source (URL) | Fact supported by the source | Placement on the map | Caveats |
| --- | --- | --- | --- | --- | --- |
| `hobbiton` | Hobbiton | [Hobbiton](https://tolkiengateway.net/wiki/Hobbiton) | A village in the central regions of the Shire, within the Westfarthing; on both sides of the Water, about a mile north-west of Bywater. | Centre of the Shire, which is west of Bree; the Brandywine is drawn as the Shire's eastern edge. | None beyond the general ones above. |
| `bree` | Bree | [Bree](https://tolkiengateway.net/wiki/Bree) | Village east of the crossing of the East Road and the Greenway; Bree-land about forty miles east of the Shire, east of the Old Forest and Barrow-downs, west of Weathertop, south of Fornost Erain. | About forty miles east of the Shire; east of the Old Forest and Barrow-downs; west of Weathertop; south of Fornost. | None beyond the general ones above. |
| `weathertop` | Weathertop | [Weathertop](https://tolkiengateway.net/wiki/Weathertop)<br>[Bree](https://tolkiengateway.net/wiki/Bree) | Southernmost top of the Weather Hills; the Weather Hills lay at the centre of Arnor, near the East Road.<br>Bree-land lay west of Weathertop. | East of Bree on the East Road, at the centre of Arnor. | None beyond the general ones above. |
| `rivendell` | Rivendell | [Rivendell](https://tolkiengateway.net/wiki/Rivendell) | An Elven outpost in the Misty Mountains on the eastern edge of Eriador; at the edge of a narrow gorge of the Bruinen, hidden in the moorlands and foothills of the Misty Mountains. | On the eastern edge of Eriador, at the foot of the Misty Mountains, on the Bruinen. | None beyond the general ones above. |
| `moria` | Moria | [Moria](https://tolkiengateway.net/wiki/Moria)<br>[Dimrill Dale](https://tolkiengateway.net/wiki/Dimrill_Dale) | Lay in the central parts of the Misty Mountains and traversed them from east to west; West-gate and East-gate at least forty miles apart.<br>Dimrill Dale was a valley on the east side of the Misty Mountains, at the eastern end of the Redhorn Pass. | West side of the Misty Mountains, central part of the range; Dimrill Dale lies across the ridge to the east. | None beyond the general ones above. |
| `lothlorien` | Lothlórien | [Lothlórien](https://tolkiengateway.net/wiki/Lothl%C3%B3rien)<br>[Caras Galadhon](https://tolkiengateway.net/wiki/Caras_Galadhon) | The forest between the mountains and the Great River, above the Limlight and below the Gladden; the only mallorn-trees east of the sea.<br>Caras Galadhon was in the angle between the rivers Celebrant and Anduin. | Between the Misty Mountains and the Anduin, east of Moria and across the river from the south-west of Mirkwood. | A forest; the point is an editorial centre, near where Caras Galadhon stood. |
| `isengard` | Isengard | [Isengard](https://tolkiengateway.net/wiki/Isengard) | A fortress in a valley at the southern end of the Misty Mountains near the Gap of Rohan; domain of Saruman. | At the southern tip of the Misty Mountains, north of the Fords of Isen. | None beyond the general ones above. |
| `helms-deep` | Helm's Deep | [Helm's Deep](https://tolkiengateway.net/wiki/Helm%27s_Deep) | A gorge below the Thrihyrne at the head of the Westfold Vale, near the end of the northernmost arm of the White Mountains; about five leagues (15 miles) from the mouth of the Deeping-coomb to the Fords of Isen. | Near the western end of the White Mountains, east-south-east of the Fords of Isen, west of Edoras. | None beyond the general ones above. |
| `edoras` | Edoras | [Edoras](https://tolkiengateway.net/wiki/Edoras)<br>[Rohan](https://tolkiengateway.net/wiki/Rohan) | Capital of Rohan with the Golden Hall of Meduseld; on a hill in Harrowdale, under the Starkhorn; the Snowbourn flowed past it west toward the Entwash.<br>Edoras lay on the slopes of the White Mountains. | On the northern slopes of the White Mountains, east of Helm's Deep and north of Dunharrow. | None beyond the general ones above. |
| `minas-tirith` | Minas Tirith | [Minas Tirith](https://tolkiengateway.net/wiki/Minas_Tirith)<br>[Osgiliath](https://tolkiengateway.net/wiki/Osgiliath)<br>[Cair Andros](https://tolkiengateway.net/wiki/Cair_Andros) | Minas Anor; capital of the South-kingdom from T.A. 1640.<br>Osgiliath lay about halfway between Minas Anor to the south-west and Minas Ithil to the north-east.<br>Cair Andros lay about fifty miles north of Minas Tirith. | South-west of Osgiliath, about fifty miles south of Cair Andros, at the east end of the White Mountains. | None beyond the general ones above. |
| `pelargir` | Pelargir | [Pelargir](https://tolkiengateway.net/wiki/Pelargir) | A great city on the Anduin and the main harbour of Gondor; left far inland by the retreat of the coast along the Bay of Belfalas. | On the Anduin, south of Osgiliath and Minas Tirith, inland from the Bay of Belfalas. | None beyond the general ones above. |
| `black-gate` | The Black Gate | [Morannon](https://tolkiengateway.net/wiki/Morannon) | The main entrance into Mordor; blocked the pass of Cirith Gorgor between the Ephel Dúath and the Ered Lithui in the north-western corner of Mordor; the Dagorlad lay further north. | North-western corner of Mordor, about 100 miles north-west of Barad-dûr. | None beyond the general ones above. |
| `mount-doom` | Mount Doom | [Mount Doom](https://tolkiengateway.net/wiki/Mount_Doom)<br>[Barad-dûr](https://tolkiengateway.net/wiki/Barad-d%C3%BBr) | A volcano in Mordor that stood alone in the Plateau of Gorgoroth, connected to the Dark Tower by Sauron's Road.<br>Barad-dûr stood about 30 miles east of Mount Doom. | In Mordor, west (and a little south) of Barad-dûr. | None beyond the general ones above. |
| `erebor` | Erebor | [Lonely Mountain](https://tolkiengateway.net/wiki/Erebor)<br>[Dale](https://tolkiengateway.net/wiki/Dale)<br>[Lake-town](https://tolkiengateway.net/wiki/Lake-town) | A large, solitary mountain in the north-east of Rhovanion and the source of the river Running.<br>The valley of Dale lay between the south-western and south-eastern arms of the Lonely Mountain.<br>Smaug dwelt in the Lonely Mountain. | North-east of Rhovanion, east of Mirkwood, north of Dale and the Long Lake. | None beyond the general ones above. |
| `grey-havens` | The Grey Havens | [Grey Havens](https://tolkiengateway.net/wiki/Grey_Havens) | Seaports on both sides of the end of the Gulf of Lune near the mouth of the Lhûn in western Eriador; ships sailed West from them. | Head of the Gulf of Lune on the far western coast of Eriador. | None beyond the general ones above. |

### Medium

| ID | Place | Source (URL) | Fact supported by the source | Placement on the map | Caveats |
| --- | --- | --- | --- | --- | --- |
| `amon-hen` | Amon Hen | [Amon Hen](https://tolkiengateway.net/wiki/Amon_Hen)<br>[Emyn Muil](https://tolkiengateway.net/wiki/Emyn_Muil) | On its eastern side at the foot lay the lawn of Parth Galen and the Anduin that flowed past Tol Brandir; the Seat of Seeing was on the summit.<br>The Fellowship broke up near the southern boundary of the Emyn Muil. | On the Anduin at the southern edge of the Emyn Muil, beside Nen Hithoel. | None beyond the general ones above. |
| `fangorn-forest` | Fangorn Forest | [Fangorn Forest](https://tolkiengateway.net/wiki/Fangorn_Forest)<br>[Fangorn (disambiguation)](https://tolkiengateway.net/wiki/Fangorn) | A deep woodland beneath the southern Misty Mountains, on the eastern side of the range; known as the Entwood in Rohan; the Entwash flowed through it.<br>A large forest to the north of Rohan where Treebeard and the other Ents live. | On the eastern flank of the southern Misty Mountains, north of Rohan. | A forest several hundred miles across; the point is an editorial centre. |
| `dead-marshes` | The Dead Marshes | [Dead Marshes](https://tolkiengateway.net/wiki/Dead_Marshes) | Reeking wetlands north-west of the Dagorlad and south-east of the Emyn Muil; Gollum revealed a hidden way across them after meeting Frodo and Sam in the Emyn Muil. | South-east of the Emyn Muil, north-west of the Dagorlad and the Black Gate. | A region of wetland; the point is an editorial centre. |
| `minas-morgul` | Minas Morgul | [Minas Morgul](https://tolkiengateway.net/wiki/Minas_Morgul)<br>[Osgiliath](https://tolkiengateway.net/wiki/Osgiliath) | Situated within the narrow Morgul Vale, guarding the pass between Ithilien and Mordor; the Morgul-road ran from Osgiliath.<br>Osgiliath lay about halfway between Minas Anor to the south-west and Minas Ithil to the north-east; Minas Ithil was taken in 2002. | In the Morgul Vale on the Ephel Dúath, north-east of Osgiliath. | The place was Minas Ithil until it was taken in T.A. 2002; the question uses the later name. |
| `osgiliath` | Osgiliath | [Osgiliath](https://tolkiengateway.net/wiki/Osgiliath) | Stood on both sides of the Anduin, about halfway between Minas Anor (south-west) and Minas Ithil (north-east), north of Emyn Arnen. | On the Anduin, halfway between Minas Tirith (south-west) and Minas Morgul (north-east). | None beyond the general ones above. |
| `cirith-ungol` | Cirith Ungol | [Cirith Ungol](https://tolkiengateway.net/wiki/Cirith_Ungol)<br>[Mordor](https://tolkiengateway.net/wiki/Mordor) | A pass through the Ephel Dúath into Mordor, in a cleft on the north-east side of the Morgul Vale, above and north of the Morgul Pass.<br>Cirith Ungol was a higher, more difficult pass guarded by Shelob and the Tower of Cirith Ungol. | In the Ephel Dúath, above and north of the Morgul Pass. | None beyond the general ones above. |
| `barad-dur` | Barad-dûr | [Barad-dûr](https://tolkiengateway.net/wiki/Barad-d%C3%BBr) | Built on the end of a long south-western spur of the Ered Lithui in the northern part of the Plateau of Gorgoroth; about 30 miles east of Mount Doom and about 100 miles south-east of the Black Gate. | About 30 miles east of Mount Doom and about 100 miles south-east of the Black Gate. | None beyond the general ones above. |
| `dol-guldur` | Dol Guldur | [Dol Guldur](https://tolkiengateway.net/wiki/Dol_Guldur)<br>[Mirkwood](https://tolkiengateway.net/wiki/Mirkwood) | A stronghold of Sauron at the highest point of a highland in the south-west of Mirkwood; originally Amon Lanc; Lothlórien lay to the west.<br>Mirkwood was a great forest east of the Anduin; Amon Lanc lay south and west of the Narrows of the Forest. | South-west of Mirkwood, east of the Anduin and of Lothlórien. | None beyond the general ones above. |
| `dale` | Dale | [Dale](https://tolkiengateway.net/wiki/Dale) | The valley lay between the south-western and south-eastern arms of the Lonely Mountain; the town was near its southern end within a loop of the river, which flowed south to the Long Lake. | Valley between the arms of the Lonely Mountain, south of Erebor, north of the Long Lake. | None beyond the general ones above. |
| `lake-town` | Lake-town | [Lake-town](https://tolkiengateway.net/wiki/Lake-town) | On the west side of the lake, a bit north of the mouth of the Forest River in a calm bay sheltered by a rock promontory; rebuilt to the north of its original location. | West side of the Long Lake, south of Dale. | Lake-town was rebuilt to the north of its original site after Smaug died; the point is the original site on the west shore. |
| `dunharrow` | Dunharrow | [Dunharrow](https://tolkiengateway.net/wiki/Dunharrow) | A fortified refuge of the Rohirrim in the White Mountains south of Edoras, between the Írensaga (north), the Starkhorn (south) and the Dwimorberg (east); the entrance to the Paths of the Dead lay below the Dwimorberg. | In the White Mountains south of Edoras. | None beyond the general ones above. |
| `barrow-downs` | The Barrow-downs | [Barrow-downs](https://tolkiengateway.net/wiki/Barrow-downs) | A series of treeless hills east of the Shire, behind the Old Forest, and south-west of Bree. | East of the Old Forest, south-west of Bree. | A range of hills; the point is an editorial centre. |
| `dol-amroth` | Dol Amroth | [Dol Amroth](https://tolkiengateway.net/wiki/Dol_Amroth) | A promontory ruled as a hereditary princedom from its stronghold on a peninsula in Gondor facing the Bay of Belfalas. | On a peninsula on the Bay of Belfalas, in the south-west of Gondor. | None beyond the general ones above. |
| `emyn-muil` | Emyn Muil | [Emyn Muil](https://tolkiengateway.net/wiki/Emyn_Muil) | A range of hills around the lake Nen Hithoel, north of the falls of Rauros; south of the Brown Lands and north of the Nindalf; the Anduin flowed through the hills. | Around Nen Hithoel, north of Rauros and of Amon Hen, south of the Brown Lands. | A range of hills; the point is an editorial centre. |
| `iron-hills` | The Iron Hills | [Iron Hills](https://tolkiengateway.net/wiki/Iron_Hills)<br>[Dale](https://tolkiengateway.net/wiki/Dale) | A range of great hills in the north-east of Wilderland, rich in iron and home to a Dwarf-realm.<br>An old road led from the valley of Dale to the east towards the Iron Hills. | North-east of Wilderland, east of Erebor and Dale. | The page gives a region ("north-east of Wilderland"), not a site; the point is an editorial centre of the range. |

### Hard

| ID | Place | Source (URL) | Fact supported by the source | Placement on the map | Caveats |
| --- | --- | --- | --- | --- | --- |
| `fornost` | Fornost | [Fornost Erain](https://tolkiengateway.net/wiki/Fornost) | Located at the south end of the North Downs, about 100 miles north of Bree, at the northern end of the North-South Road; captured by Angmar in T.A. 1974. | About 100 miles north of Bree, at the south end of the North Downs. | Captured in T.A. 1974; a ruin in the War of the Ring. |
| `carn-dum` | Carn Dûm | [Carn Dûm](https://tolkiengateway.net/wiki/Carn_D%C3%BBm)<br>[Mount Gundabad](https://tolkiengateway.net/wiki/Mount_Gundabad) | The chief fortress of Angmar and the seat of its king, at the north-western end of the Mountains of Angmar.<br>The Misty Mountains continued past Mount Gundabad in a north-westerly direction as the Mountains of Angmar; Angmar was destroyed in 1975. | North-western end of the Mountains of Angmar, which run north-west from Mount Gundabad. | Seat of Angmar, which was destroyed in T.A. 1975; not an inhabited fortress in the War of the Ring. |
| `annuminas` | Annúminas | [Annúminas](https://tolkiengateway.net/wiki/Ann%C3%BAminas) | On the south-east shore of Lake Nenuial, near the source of the Baranduin; the Hills of Evendim rose to the south and west; the capital later moved to Fornost Erain. | South-east shore of Lake Evendim, north-west of Fornost. | Ruined by the later Third Age; Aragorn's later house there belongs to the Fourth Age. |
| `tharbad` | Tharbad | [Tharbad](https://tolkiengateway.net/wiki/Tharbad) | On the southern border of Eriador, a few miles downstream from the confluence of the Hoarwell and the Glanduin; the North-South Road crossed the Gwathló there; ruined and deserted in T.A. 2912. | Southern border of Eriador, on the Gwathló, south of Bree and south-west of Eregion. | Ruined and deserted after the floods of T.A. 2912, so in the War of the Ring it was a ruin. |
| `ost-in-edhil` | Ost-in-Edhil | [Eregion](https://tolkiengateway.net/wiki/Eregion)<br>[Ost-in-Edhil](https://tolkiengateway.net/wiki/Ost-in-Edhil)<br>[Rivendell](https://tolkiengateway.net/wiki/Rivendell) | Eregion lay in Eriador west of the Misty Mountains near the West-gate of Moria; Ost-in-Edhil was its capital; a high road ran from Ost-in-Edhil to Khazad-dûm.<br>The highroad connected Ost-in-Edhil with the west gate of Moria; its exact location is uncertain, possibly near the Sirannon many miles downstream from the West-gate.<br>Elrond founded Rivendell in S.A. 1697 after the Fall of Eregion. | In Eregion (Hollin), west of the Misty Mountains and of Moria's West-gate. | The page itself says the exact site is uncertain (possibly on the Sirannon, many miles downstream from the West-gate). The point is an editorial placement in Hollin, accurate only to a wide band. Second Age city: it did not stand in the Third Age (see "Ages"). |
| `mount-gundabad` | Mount Gundabad | [Mount Gundabad](https://tolkiengateway.net/wiki/Mount_Gundabad)<br>[Misty Mountains](https://tolkiengateway.net/wiki/Misty_Mountains) | Appeared as the northern endpoint of the Misty Mountains and the western endpoint of the Grey Mountains; the Misty Mountains continued past it north-westwards as the Mountains of Angmar; usually occupied by Orcs.<br>In the far north the Misty Mountains met the Grey Mountains and the Mountains of Angmar, forming a T; Gundabad is the northernmost peak. | Where the Misty, Grey and Angmar mountains meet, north of Rivendell. | TG describes it as adjacent to the northern Misty Mountains; its exact bearing from Rivendell is an editorial placement. |
| `dimrill-dale` | Dimrill Dale | [Dimrill Dale](https://tolkiengateway.net/wiki/Dimrill_Dale)<br>[Moria](https://tolkiengateway.net/wiki/Moria) | A valley on the east side of the Misty Mountains beneath the Redhorn, Silvertine and Cloudyhead; source of the Silverlode, with the Mirrormere at its south-east corner.<br>The Battle of Nanduhirion was fought in the valley beneath Moria's East-gate. | East side of the Misty Mountains, immediately east of Moria. | The TG page is titled Dimrill Dale; Azanulbizar is its Dwarvish name (the URL Azanulbizar redirects to it). |
| `beorns-hall` | Beorn's Hall | [Beorn's Hall](https://tolkiengateway.net/wiki/Beorn%27s_Hall) | The Hall lay near the Carrock, east of the Great River of Wilderland, just west of Mirkwood. | East of the Anduin, just west of Mirkwood, near the Carrock. | The page gives a relation to the Carrock and Mirkwood, not a distance; the point is an editorial placement. |
| `fords-of-isen` | The Fords of Isen | [Fords of Isen](https://tolkiengateway.net/wiki/Fords_of_Isen) | A few miles north of where the Isen turned west toward the sea in the Gap of Rohan; the only place the river could be crossed south of Isengard; the main entrance to Rohan from Eriador. | South of Isengard, in the Gap of Rohan where the river bends west. | None beyond the general ones above. |
| `cair-andros` | Cair Andros | [Cair Andros](https://tolkiengateway.net/wiki/Cair_Andros) | A long, narrow river-island in the Anduin, a little over ten miles long, east of Anórien and about fifty miles north of Minas Tirith. | In the Anduin, east of Anórien and about fifty miles north of Minas Tirith. | None beyond the general ones above. |
| `isenmouthe` | The Isenmouthe | [Carach Angren](https://tolkiengateway.net/wiki/Isenmouthe) | A pass in the north-west of Mordor formed where spurs of the Ephel Dúath and the Ered Lithui met, leaving a narrow passage between the Plateau of Gorgoroth and the valley of Udûn to the north. | North-west of Mordor, between the Plateau of Gorgoroth and Udûn, south of the Black Gate. | The TG page is titled Carach Angren; Isenmouthe is its common-speech name (the URL redirects). |
| `umbar` | Umbar | [Umbar](https://tolkiengateway.net/wiki/Umbar) | A realm on the coast south of Gondor, south of the mouth of the Anduin at the Bay of Belfalas, in a natural haven formed by a peninsula. | On the coast south of the mouth of the Anduin, at the Bay of Belfalas. | The page gives the haven and its position relative to the Anduin only loosely; the point is an editorial placement on the peninsula. |
| `edhellond` | Edhellond | [Edhellond](https://tolkiengateway.net/wiki/Edhellond) | An ancient harbour in southern Gondor, south-west of where the Ringló entered the Morthond, near where the combined waters flowed into Cobas Haven; many of the Galadhrim sailed from it in T.A. 1980. | On the coast of southern Gondor, west of Dol Amroth. | Founded in the late First Age, but the harbour on this map is the Third-Age one in Gondor, not a place in Beleriand. |
| `henneth-annun` | Henneth Annûn | [Henneth Annûn](https://tolkiengateway.net/wiki/Henneth_Ann%C3%BBn) | A hidden Gondorian outpost north-east of Cair Andros in North Ithilien; a cave behind a west-facing waterfall. | North-east of Cair Andros in North Ithilien. | The page gives only "north-east of Cair Andros in North Ithilien"; the point follows that direction. |
| `old-forest` | The Old Forest | [Old Forest](https://tolkiengateway.net/wiki/Old_Forest) | A small forested area east of the Shire, bordered by the Barrow-downs in the east and the Brandywine in the west; the Withywindle lay roughly in its centre. | East of the Shire and the Brandywine, west of the Barrow-downs. | A small forest; the point is an editorial centre between the Brandywine and the Barrow-downs. |

## Not verified

- No statement was checked against the books themselves.
- The map positions are editorial; they follow the relations above, not measured coordinates.
- The distances in miles quoted by the pages are the wiki's figures; they are not used to scale the map.
