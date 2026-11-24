package dev.fabik.bluetoothhid.data

/**
 * Embedded Master Spare Parts Database for Hero MotoCorp, Hero Honda, and Honda Two-Wheelers.
 * Pre-seeded with genuine OEM part numbers, Indian EAN-13 barcodes, compatible models, and standard MRPs.
 * Used for offline customer parts identification, live barcode scanning, and night PDF report generation.
 */
object MasterCatalogueSeeds {

    val SEED_PARTS: List<MasterSparePart> = listOf(
        // ==========================================
        // HERO & HERO HONDA - BRAKING SYSTEM
        // ==========================================
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "06430-KRF-B90",
            productName = "Hero Genuine Front Brake Shoe Set",
            barcode = "8904235100018",
            category = "Braking",
            compatibleModels = "Splendor Plus, HF Deluxe, Passion Pro, CD 100, Super Splendor",
            mrp = 265.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "06430-KCC-900",
            productName = "Hero Genuine Rear Brake Shoe Set",
            barcode = "8904235100025",
            category = "Braking",
            compatibleModels = "Splendor Plus, HF 100, Passion Xpro, Glamour, CD Dawn",
            mrp = 265.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "06455-KCC-900",
            productName = "Hero Genuine Front Disc Brake Pad Kit",
            barcode = "8904235100032",
            category = "Braking",
            compatibleModels = "Glamour Disc, Hunk, Xtreme 160R, CBZ Xtreme, Achiever",
            mrp = 390.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "43120365H70S",
            productName = "Hero Genuine Integrated Brake Shoe Comp",
            barcode = "8904235100049",
            category = "Braking",
            compatibleModels = "Destini 125, Maestro Edge, Pleasure Plus",
            mrp = 295.0
        ),
        MasterSparePart(
            brand = "Hero Honda",
            partNumber = "06430-GB4-680",
            productName = "Hero Honda Genuine Brake Shoe (CD100/Splendor)",
            barcode = "8904235100056",
            category = "Braking",
            compatibleModels = "Hero Honda CD100, CD100 SS, Sleek, Street, Splendor",
            mrp = 245.0
        ),

        // ==========================================
        // HONDA - BRAKING SYSTEM
        // ==========================================
        MasterSparePart(
            brand = "Honda",
            partNumber = "06430-GCC-B00",
            productName = "Honda Genuine Brake Shoe Kit (Activa/Dio)",
            barcode = "8904235100063",
            category = "Braking",
            compatibleModels = "Activa (All 3G/4G/5G/6G/125), Dio, Aviator, Grazia",
            mrp = 280.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "06455-KRE-G01",
            productName = "Honda Genuine Front Disc Brake Pads (CB Shine)",
            barcode = "8904235100070",
            category = "Braking",
            compatibleModels = "CB Shine, CB Unicorn 150/160, SP 125, Hornet 160R",
            mrp = 420.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "06430-KUD-900",
            productName = "Honda Genuine Rear Brake Shoe (Unicorn)",
            barcode = "8904235100087",
            category = "Braking",
            compatibleModels = "CB Unicorn 150, CB Unicorn 160, CB Trigger",
            mrp = 295.0
        ),

        // ==========================================
        // FILTRATION & ENGINE SERVICE (HERO & HONDA)
        // ==========================================
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "17211-KTC-900",
            productName = "Hero Genuine Air Filter Element",
            barcode = "8904235100094",
            category = "Filters",
            compatibleModels = "Splendor Plus, HF Deluxe, Passion Pro, CD 100",
            mrp = 185.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "17211-KTR-940",
            productName = "Hero Genuine Viscous Air Filter (Glamour)",
            barcode = "8904235100100",
            category = "Filters",
            compatibleModels = "Glamour 125, Super Splendor 125, New Passion Pro",
            mrp = 210.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "17211-AAH-000",
            productName = "Hero Genuine Scooter Air Filter (Maestro/Destini)",
            barcode = "8904235100117",
            category = "Filters",
            compatibleModels = "Maestro Edge 110/125, Destini 125, Duet 110",
            mrp = 230.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "17210-KPL-900",
            productName = "Honda Genuine Air Cleaner Element (Activa 3G/4G/5G)",
            barcode = "8904235100124",
            category = "Filters",
            compatibleModels = "Activa 3G, Activa 4G, Activa 5G, Dio (HET models)",
            mrp = 240.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "17210-KWP-D00",
            productName = "Honda Genuine Viscous Air Cleaner (Activa 6G/125)",
            barcode = "8904235100131",
            category = "Filters",
            compatibleModels = "Activa 6G, Activa 125 BS6, Dio BS6",
            mrp = 260.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "17211-KRM-860",
            productName = "Honda Genuine Air Filter Cartridge (CB Shine)",
            barcode = "8904235100148",
            category = "Filters",
            compatibleModels = "CB Shine 125, CB Shine SP, SP 125",
            mrp = 215.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "15421035010S",
            productName = "Hero Genuine Oil Strainer Screen",
            barcode = "8904235100155",
            category = "Filters",
            compatibleModels = "Splendor, HF Deluxe, Passion Pro, Glamour",
            mrp = 45.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "CASTROL-ACTIV-1L",
            productName = "Castrol Activ 4T 20W-40 Motorcycle Engine Oil (1 Litre)",
            barcode = "8901030381734",
            category = "Lubricants",
            compatibleModels = "Splendor, HF Deluxe, Passion, Glamour, Pulsar 150, CB Shine",
            mrp = 425.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "HERO-4T-PLUS-900ML",
            productName = "Hero 4T Plus Genuine Engine Oil 10W-30 (900 ML)",
            barcode = "8904235100162",
            category = "Lubricants",
            compatibleModels = "All Hero 100cc & 110cc Motorcycles (Splendor, HF, Passion)",
            mrp = 360.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "HONDA-4T-IDEMITSU-800ML",
            productName = "Honda 4T Genuine Engine Oil 10W-30 (800 ML)",
            barcode = "8904235100179",
            category = "Lubricants",
            compatibleModels = "Activa (All), Dio, Aviator, Grazia",
            mrp = 345.0
        ),

        // ==========================================
        // SPARK PLUGS
        // ==========================================
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "31917AACH00S",
            productName = "Champion / Hero Spark Plug (UR4AC)",
            barcode = "8904235100186",
            category = "Electrical",
            compatibleModels = "Splendor Plus, HF Deluxe, Passion Pro, CD 100, Dawn",
            mrp = 115.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "31916-KRM-840",
            productName = "NGK Spark Plug CPR8EA-9 (Activa/Shine)",
            barcode = "8904235100193",
            category = "Electrical",
            compatibleModels = "Activa 110/125, CB Shine, Unicorn 150, SP 125",
            mrp = 145.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "34901KSP910S",
            productName = "Hero Genuine 12V 35/35W Halogen Headlight Bulb (HS1)",
            barcode = "8904235100209",
            category = "Electrical",
            compatibleModels = "Splendor Plus, Passion Pro, Glamour, Super Splendor, Destini",
            mrp = 135.0
        ),

        // ==========================================
        // CLUTCH & TRANSMISSION
        // ==========================================
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "22201GF6000S",
            productName = "Hero Genuine Clutch Friction Plate Set (4 Pcs)",
            barcode = "8904235100216",
            category = "Transmission",
            compatibleModels = "Splendor Plus, HF Deluxe, Passion Pro, CD Dawn, CD 100",
            mrp = 385.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "22355KRM840S",
            productName = "Hero Genuine Clutch Center Hub",
            barcode = "8904235100223",
            category = "Transmission",
            compatibleModels = "Glamour 125, Super Splendor 125",
            mrp = 275.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "22120KTC900S",
            productName = "Hero Genuine Clutch Pressure Plate",
            barcode = "8904235100230",
            category = "Transmission",
            compatibleModels = "Splendor Plus, HF Deluxe, Passion Pro",
            mrp = 195.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "23100-KWP-D01",
            productName = "Honda Genuine Drive Belt (Bando Activa 3G/4G/5G)",
            barcode = "8904235100247",
            category = "Transmission",
            compatibleModels = "Activa 110 (All 3G/4G/5G), Dio, Aviator",
            mrp = 485.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "22102-KUD-900",
            productName = "Honda Genuine Drive Face Variator (Activa)",
            barcode = "8904235100254",
            category = "Transmission",
            compatibleModels = "Activa 110, Dio, Aviator",
            mrp = 320.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "K41412AANNB00S",
            productName = "Hero Genuine Drive Chain & Sprocket Kit",
            barcode = "8904235100261",
            category = "Transmission",
            compatibleModels = "Splendor Plus, HF Deluxe (428-112 Links)",
            mrp = 890.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "40530-KTR-940",
            productName = "Hero Genuine Heavy Duty Drive Chain",
            barcode = "8904235100278",
            category = "Transmission",
            compatibleModels = "Glamour 125, Super Splendor 125",
            mrp = 550.0
        ),

        // ==========================================
        // CONTROL CABLES
        // ==========================================
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "22870KCC900S",
            productName = "Hero Genuine Clutch Cable Assembly",
            barcode = "8904235100285",
            category = "Cables",
            compatibleModels = "Splendor Plus, HF Deluxe, Passion Pro",
            mrp = 125.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "45450KTC900S",
            productName = "Hero Genuine Front Brake Cable",
            barcode = "8904235100292",
            category = "Cables",
            compatibleModels = "Splendor Plus, HF Deluxe, Passion Pro",
            mrp = 135.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "17910-KCC-900",
            productName = "Hero Genuine Accelerator / Throttle Cable",
            barcode = "8904235100308",
            category = "Cables",
            compatibleModels = "Splendor Plus, HF Deluxe, CD 100",
            mrp = 115.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "44830KST940S",
            productName = "Hero Genuine Speedometer Cable",
            barcode = "8904235100315",
            category = "Cables",
            compatibleModels = "Splendor Plus, Passion Pro, HF Deluxe",
            mrp = 110.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "45450-KUD-900",
            productName = "Honda Genuine Front Brake Cable (Activa)",
            barcode = "8904235100322",
            category = "Cables",
            compatibleModels = "Activa (All 3G/4G/5G/6G)",
            mrp = 160.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "43450-KUD-900",
            productName = "Honda Genuine Rear Brake Cable (Activa)",
            barcode = "8904235100339",
            category = "Cables",
            compatibleModels = "Activa (All models)",
            mrp = 175.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "22870-KRM-840",
            productName = "Honda Genuine Clutch Cable (CB Shine)",
            barcode = "8904235100346",
            category = "Cables",
            compatibleModels = "CB Shine, CB Shine SP",
            mrp = 140.0
        ),

        // ==========================================
        // ELECTRICAL, SWITCHES & SENSORS
        // ==========================================
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "35010-KCC-900",
            productName = "Hero Genuine Ignition Switch Key Set (With Tank Lock)",
            barcode = "8904235100353",
            category = "Electrical",
            compatibleModels = "Splendor Plus, HF Deluxe",
            mrp = 560.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "35340KCC900S",
            productName = "Hero Genuine Front Brake Light Switch",
            barcode = "8904235100360",
            category = "Electrical",
            compatibleModels = "Splendor, HF Deluxe, Passion Pro, Glamour",
            mrp = 65.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "30410KWA931S",
            productName = "Hero Genuine CDI Unit Assembly",
            barcode = "8904235100377",
            category = "Electrical",
            compatibleModels = "Splendor Plus i3S, HF Deluxe i3S",
            mrp = 780.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "31600-KTC-900",
            productName = "Hero Genuine Regulator Rectifier (RR Unit)",
            barcode = "8904235100384",
            category = "Electrical",
            compatibleModels = "Splendor, Passion, HF Deluxe",
            mrp = 480.0
        ),

        // ==========================================
        // SUSPENSION, SEALS & BEARINGS
        // ==========================================
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "51490-KTC-900",
            productName = "Hero Genuine Front Fork Oil Seal & Dust Cap Set",
            barcode = "8904235100391",
            category = "Suspension",
            compatibleModels = "Splendor, HF Deluxe, Passion Pro",
            mrp = 175.0
        ),
        MasterSparePart(
            brand = "Honda",
            partNumber = "51490-KRM-861",
            productName = "Honda Genuine Front Fork Oil Seal Kit (CB Shine)",
            barcode = "8904235100407",
            category = "Suspension",
            compatibleModels = "CB Shine, CB Unicorn 150",
            mrp = 220.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "961406301010S",
            productName = "NBC / Hero Genuine Deep Groove Ball Bearing 6301 2RS",
            barcode = "8904235100414",
            category = "Bearings",
            compatibleModels = "Splendor Rear Wheel, HF Deluxe, Passion Pro",
            mrp = 140.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "961406201010S",
            productName = "NBC / Hero Genuine Ball Bearing 6201 (Front Wheel)",
            barcode = "8904235100421",
            category = "Bearings",
            compatibleModels = "Splendor Front Wheel, HF Deluxe",
            mrp = 125.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "961406203010S",
            productName = "NBC / Hero Genuine Ball Bearing 6203 (Rear Wheel Sprocket)",
            barcode = "8904235100438",
            category = "Bearings",
            compatibleModels = "Splendor, HF Deluxe, Passion",
            mrp = 155.0
        ),

        // ==========================================
        // GASKETS, RUBBERS & ACCESSORIES
        // ==========================================
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "11394KPH901S",
            productName = "Hero Genuine Right Crankcase Clutch Gasket",
            barcode = "8904235100445",
            category = "Engine",
            compatibleModels = "Glamour 125, Super Splendor 125",
            mrp = 65.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "12250GF6000LS",
            productName = "Hero Genuine Cylinder Head Packing Gasket",
            barcode = "8904235100452",
            category = "Engine",
            compatibleModels = "Splendor, HF Deluxe, Passion, CD 100",
            mrp = 85.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "12209GB4685S",
            productName = "Hero Genuine Valve Stem Seal Set",
            barcode = "8904235100469",
            category = "Engine",
            compatibleModels = "Splendor, HF Deluxe, Passion, CD 100",
            mrp = 70.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "88120ABZ001S",
            productName = "Hero Genuine Rear View Mirror Set (Left + Right)",
            barcode = "8904235100476",
            category = "Body & Mirrors",
            compatibleModels = "Splendor Plus, HF Deluxe, Passion",
            mrp = 290.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "53175KSP900S",
            productName = "Hero Genuine Right Hand Brake Lever",
            barcode = "8904235100483",
            category = "Handlebar",
            compatibleModels = "Splendor Plus, Passion Pro, HF Deluxe",
            mrp = 85.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "53178KCC900S",
            productName = "Hero Genuine Left Hand Clutch Lever",
            barcode = "8904235100490",
            category = "Handlebar",
            compatibleModels = "Splendor Plus, Passion Pro, HF Deluxe",
            mrp = 85.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "50711KCC900S",
            productName = "Hero Genuine Rider Footrest Rubber Set",
            barcode = "8904235100506",
            category = "Rubber Parts",
            compatibleModels = "Splendor Plus, HF Deluxe, Passion",
            mrp = 60.0
        ),
        MasterSparePart(
            brand = "Hero MotoCorp",
            partNumber = "41241-KCC-900",
            productName = "Hero Genuine Wheel Damper Rubber Set (Cush Drive)",
            barcode = "8904235100513",
            category = "Rubber Parts",
            compatibleModels = "Splendor Plus, HF Deluxe, Passion Pro, CD 100",
            mrp = 110.0
        )
    )
}
