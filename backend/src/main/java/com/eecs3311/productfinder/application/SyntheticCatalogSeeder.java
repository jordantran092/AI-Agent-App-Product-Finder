package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.PricePoint;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.catalog.Retailer;
import com.eecs3311.productfinder.domain.catalog.Review;
import com.eecs3311.productfinder.infrastructure.persistence.ListingRepository;
import com.eecs3311.productfinder.infrastructure.persistence.PricePointRepository;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import com.eecs3311.productfinder.infrastructure.persistence.RetailerRepository;
import com.eecs3311.productfinder.infrastructure.persistence.ReviewRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SyntheticCatalogSeeder implements ApplicationRunner {

    private static final int TARGET_PRODUCT_COUNT = 2500;
    private static final Map<ProductCategory, Integer> CATEGORY_TARGETS = Map.of(
            ProductCategory.SMARTPHONE, 313,
            ProductCategory.LAPTOP, 313,
            ProductCategory.TABLET, 313,
            ProductCategory.TELEVISION, 313,
            ProductCategory.HEADPHONES, 312,
            ProductCategory.CLOTHING_TOP, 312,
            ProductCategory.CLOTHING_BOTTOM, 312,
            ProductCategory.FOOTWEAR, 312
    );
    private static final String[] COLORS = {"Black", "Silver", "Blue", "White", "Graphite"};
    private static final String[] COLOR_CODES = {"BLK", "SLV", "BLU", "WHT", "GRY"};
    private static final int[] STORAGE_GB = {128, 256, 512, 1024};
    private static final String[][] FAMILIES = {
            {"Samsung", "Galaxy S25", "SM-S931W"},
            {"Apple", "iPhone 16", "A3081"},
            {"Google", "Pixel 9", "GUR25"},
            {"OnePlus", "OnePlus 13", "CPH2653"},
            {"Motorola", "Edge 50", "XT2407"},
            {"Samsung", "Galaxy A56", "SM-A566W"},
            {"Apple", "iPhone 16e", "A3212"},
            {"Google", "Pixel 9a", "GTF7P"}
    };

    private final ProductRepository productRepository;
    private final RetailerRepository retailerRepository;
    private final ListingRepository listingRepository;
    private final PricePointRepository pricePointRepository;
    private final ReviewRepository reviewRepository;

    public SyntheticCatalogSeeder(
            ProductRepository productRepository,
            RetailerRepository retailerRepository,
            ListingRepository listingRepository,
            PricePointRepository pricePointRepository,
            ReviewRepository reviewRepository
    ) {
        this.productRepository = productRepository;
        this.retailerRepository = retailerRepository;
        this.listingRepository = listingRepository;
        this.pricePointRepository = pricePointRepository;
        this.reviewRepository = reviewRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() >= TARGET_PRODUCT_COUNT) {
            return;
        }

        List<Product> existingProducts = productRepository.findAll();
        EnumMap<ProductCategory, Integer> categoryCounts = new EnumMap<>(ProductCategory.class);
        for (ProductCategory category : ProductCategory.values()) {
            categoryCounts.put(category, 0);
        }
        existingProducts.forEach(product -> categoryCounts.compute(
                product.getCategory(), (category, count) -> count == null ? 1 : count + 1
        ));

        List<Product> newProducts = new ArrayList<>(TARGET_PRODUCT_COUNT - existingProducts.size());
        for (ProductCategory category : ProductCategory.values()) {
            int currentCount = categoryCounts.get(category);
            int needed = Math.max(0, CATEGORY_TARGETS.get(category) - currentCount);
            for (int variant = currentCount; variant < currentCount + needed; variant++) {
                newProducts.add(createProduct(category, variant));
            }
        }

        productRepository.saveAll(newProducts);
        productRepository.flush();
        addRetailerData(newProducts);
    }

    private void addRetailerData(List<Product> products) {
        Retailer amazon = retailer("Amazon.ca");
        Retailer bestBuy = retailer("Best Buy Canada");
        Retailer appleStore = retailer("Apple Store");
        Retailer sportChek = retailer("Sport Chek");
        Retailer nikeCanada = retailer("Nike Canada");
        Retailer levisCanada = retailer("Levi's Canada");

        List<Listing> listings = new ArrayList<>(products.size() * 2);
        List<Review> reviews = new ArrayList<>(products.size() * 2);
        for (Product product : products) {
            Retailer secondRetailer = secondRetailer(product, bestBuy, appleStore, sportChek, nikeCanada, levisCanada);
            BigDecimal basePrice = price(product.getCategory(), product.getId().intValue());
            Listing amazonListing = new Listing(
                    product, amazon, basePrice.subtract(new BigDecimal("10.00")), ProductCondition.NEW, true
            );
            Listing otherListing = new Listing(
                    product, secondRetailer, basePrice, ProductCondition.NEW, true
            );
            listings.add(amazonListing);
            listings.add(otherListing);
            reviews.add(review(product, amazon, product.getId().intValue(), "Amazon.ca"));
            reviews.add(review(product, secondRetailer, product.getId().intValue(), secondRetailer.getName()));
        }
        listingRepository.saveAll(listings);
        listingRepository.flush();

        OffsetDateTime now = OffsetDateTime.now();
        List<PricePoint> pricePoints = new ArrayList<>(listings.size() * 2);
        for (Listing listing : listings) {
            pricePoints.add(new PricePoint(listing, now.minusMonths(2), listing.getPrice().add(new BigDecimal("20.00"))));
            pricePoints.add(new PricePoint(listing, now.minusMonths(1), listing.getPrice()));
        }
        pricePointRepository.saveAll(pricePoints);
        reviewRepository.saveAll(reviews);
    }

    private Product createProduct(ProductCategory category, int variant) {
        return switch (category) {
            case SMARTPHONE -> smartphone(variant);
            case LAPTOP -> laptop(variant);
            case TABLET -> tablet(variant);
            case TELEVISION -> television(variant);
            case HEADPHONES -> headphones(variant);
            case CLOTHING_TOP -> clothingTop(variant);
            case CLOTHING_BOTTOM -> clothingBottom(variant);
            case FOOTWEAR -> footwear(variant);
        };
    }

    private Product smartphone(int variant) {
        String[] family = FAMILIES[variant % FAMILIES.length];
        int storage = STORAGE_GB[(variant / FAMILIES.length) % STORAGE_GB.length];
        String color = COLORS[(variant / FAMILIES.length) % COLORS.length];
        String model = family[2] + "-" + storage + "GB-" + COLOR_CODES[variant % COLOR_CODES.length]
                + "-" + sequence(variant);
        int memory = family[0].equals("Apple") ? 8 : 12;
        int battery = 3900 + (variant % 9) * 100;
        Map<String, Object> specs = Map.of(
                "displaySizeInches", 6.1 + (variant % 5) * 0.1,
                "resolution", "2340x1080",
                "operatingSystem", family[0].equals("Apple") ? "iOS 18" : "Android 15",
                "memoryGb", memory,
                "storageGb", storage,
                "batteryMah", battery,
                "connectivity", List.of("5G", "Wi-Fi 7", "Bluetooth 5.4")
        );
        return product(family[1] + " " + storage + " GB " + color, family[0], model,
                ProductCategory.SMARTPHONE, "Synthetic " + family[1] + " configuration with " + storage
                        + " GB storage in " + color + ".", specs);
    }

    private Product laptop(int variant) {
        String[][] families = {
                {"Apple", "MacBook Air 13-inch (M4)", "A3240", "Apple M4", "Apple M4 GPU", "macOS Sequoia"},
                {"Apple", "MacBook Pro 14-inch (M4)", "A3112", "Apple M4", "Apple M4 GPU", "macOS Sequoia"},
                {"Lenovo", "ThinkPad T14 Gen 5", "21ML", "Intel Core Ultra 7", "Intel Arc Graphics", "Windows 11"},
                {"Dell", "XPS 13 9345", "9345", "Snapdragon X Plus", "Qualcomm Adreno", "Windows 11"},
                {"ASUS", "Zenbook 14 OLED UX3405", "UX3405", "Intel Core Ultra 7", "Intel Arc Graphics", "Windows 11"},
                {"HP", "OmniBook Ultra Flip 14", "14-FH", "Intel Core Ultra 7", "Intel Arc Graphics", "Windows 11"},
                {"Acer", "Swift Go 14", "SFG14", "Intel Core Ultra 5", "Intel Arc Graphics", "Windows 11"},
                {"Microsoft", "Surface Laptop 7 13-inch", "2036", "Snapdragon X Plus", "Qualcomm Adreno", "Windows 11"}
        };
        String[] family = families[variant % families.length];
        int memory = 16 + (variant % 3) * 16;
        int storage = new int[]{256, 512, 1024}[(variant / families.length) % 3];
        String model = family[2] + "-" + memory + "GB-" + storage + "GB-" + sequence(variant);
        Map<String, Object> specs = Map.of(
                "displaySizeInches", 13.0 + (variant % 4) * 0.5,
                "resolution", "2880x1800",
                "processor", family[3],
                "memoryGb", memory,
                "storageType", "SSD",
                "storageGb", storage,
                "graphics", family[4],
                "operatingSystem", family[5]
        );
        return product(family[1] + " " + memory + " GB / " + storage + " GB", family[0], model,
                ProductCategory.LAPTOP, "Synthetic " + family[1] + " configuration with " + memory
                        + " GB memory and " + storage + " GB SSD storage.", specs);
    }

    private Product tablet(int variant) {
        String[][] families = {
                {"Apple", "iPad Air 11-inch (M3)", "A3266", "iPadOS 18"},
                {"Apple", "iPad Pro 11-inch (M4)", "A2836", "iPadOS 18"},
                {"Samsung", "Galaxy Tab S10+", "SM-X820", "Android 14"},
                {"Samsung", "Galaxy Tab S10 FE", "SM-X520", "Android 15"},
                {"Google", "Pixel Tablet", "GTU8P", "Android 14"},
                {"Lenovo", "Tab P12", "TB370FU", "Android 13"},
                {"Microsoft", "Surface Pro 11", "2038", "Windows 11"},
                {"OnePlus", "OnePlus Pad 2", "OPD2404", "Android 14"}
        };
        String[] family = families[variant % families.length];
        int storage = STORAGE_GB[(variant / families.length) % STORAGE_GB.length];
        Map<String, Object> specs = Map.of(
                "displaySizeInches", 10.9 + (variant % 4) * 0.5,
                "resolution", "2560x1600",
                "operatingSystem", family[3],
                "memoryGb", family[0].equals("Apple") ? 8 : 12,
                "storageGb", storage,
                "connectivity", List.of("Wi-Fi 6E", "Bluetooth 5.3"),
                "stylusSupport", true
        );
        return product(family[1] + " " + storage + " GB", family[0],
                family[2] + "-" + storage + "GB-" + sequence(variant),
                ProductCategory.TABLET, "Synthetic " + family[1] + " configuration with " + storage
                        + " GB storage and stylus support.", specs);
    }

    private Product television(int variant) {
        String[][] families = {
                {"Samsung", "OLED S90D", "QN"},
                {"LG", "OLED evo C4", "OLED"},
                {"Sony", "BRAVIA XR A80L", "XR"},
                {"TCL", "QM8 QD-Mini LED", "QM"},
                {"Hisense", "U8N Mini-LED ULED", "U8"},
                {"Samsung", "Neo QLED QN90D", "QN"},
                {"LG", "QNED evo QNED90", "QNED"},
                {"Sony", "BRAVIA 7 Mini LED", "K"}
        };
        String[] family = families[variant % families.length];
        int size = new int[]{43, 50, 55, 65, 75, 85}[(variant / families.length) % 6];
        String panel = family[1].contains("OLED") || family[1].contains("A80L") ? "OLED"
                : family[1].contains("Mini") || family[1].contains("Neo")
                || family[1].contains("QNED") ? "Mini LED" : "QLED";
        String model = family[2] + size + family[1].replaceAll("[^A-Z0-9]", "").substring(0, 3)
                + "-" + sequence(variant);
        Map<String, Object> specs = Map.of(
                "screenSizeInches", size,
                "resolution", "3840x2160",
                "panelType", panel,
                "refreshRateHz", variant % 2 == 0 ? 120 : 60,
                "hdrSupport", List.of("HDR10", "Dolby Vision"),
                "smartTvPlatform", family[0].equals("Samsung") ? "Tizen"
                        : family[0].equals("LG") ? "webOS" : family[0].equals("Sony") ? "Google TV" : "Google TV"
        );
        return product(family[0] + " " + family[1] + " " + size + "-inch 4K TV", family[0], model,
                ProductCategory.TELEVISION, "Synthetic " + size + "-inch " + panel + " television configuration.",
                specs);
    }

    private Product headphones(int variant) {
        String[][] families = {
                {"Sony", "WH-1000XM5", "WH1000XM5"},
                {"Bose", "QuietComfort Ultra", "QCULTRA"},
                {"Apple", "AirPods Max", "A2096"},
                {"Sennheiser", "Momentum 4 Wireless", "M4AEBT"},
                {"JBL", "Tour One M2", "TOURONEM2"},
                {"Beats", "Studio Pro", "STUDIOPRO"},
                {"Sony", "WF-1000XM5", "WF1000XM5"},
                {"Bose", "QuietComfort Ultra Earbuds", "QCUE"}
        };
        String[] family = families[variant % families.length];
        boolean inEar = family[1].contains("WF-") || family[1].contains("Earbuds");
        Map<String, Object> specs = Map.of(
                "formFactor", inEar ? "in-ear" : "over-ear",
                "connectivity", "wireless",
                "noiseCancellation", true,
                "batteryLifeHours", inEar ? 8 : 24 + (variant % 4) * 2,
                "supportedConnectionTypes", List.of("Bluetooth 5.3", "USB-C")
        );
        return product(family[0] + " " + family[1] + " Wireless Headphones", family[0],
                family[2] + "-" + COLORS[variant % COLORS.length].toUpperCase() + "-" + sequence(variant),
                ProductCategory.HEADPHONES, "Synthetic " + family[1]
                        + " wireless headphones with noise cancellation.", specs);
    }

    private Product clothingTop(int variant) {
        String[][] families = {
                {"Nike", "Sportswear Club T-Shirt", "AR4997"},
                {"Adidas", "Essentials 3-Stripes T-Shirt", "IC9280"},
                {"Under Armour", "Tech 2.0 Short Sleeve", "1326413"},
                {"Lululemon", "Metal Vent Tech Short Sleeve", "MVT"},
                {"Puma", "Essentials Logo Tee", "586666"},
                {"Nike", "Dri-FIT Legend Training T-Shirt", "DX0989"},
                {"Adidas", "Designed for Training Tee", "IC7425"},
                {"The North Face", "Simple Dome T-Shirt", "NF0A"}
        };
        String[] family = families[variant % families.length];
        String size = new String[]{"XS", "S", "M", "L", "XL", "XXL"}[(variant / families.length) % 6];
        String color = COLORS[variant % COLORS.length];
        Map<String, Object> specs = Map.of(
                "garmentType", "t-shirt",
                "size", size,
                "sizeSystem", "international",
                "fit", variant % 3 == 0 ? "slim" : "regular",
                "material", variant % 2 == 0 ? "cotton blend" : "polyester blend",
                "color", color.toLowerCase(),
                "sleeveStyle", "short"
        );
        return product(family[0] + " " + family[1] + " " + size + " " + color, family[0],
                family[2] + "-" + COLOR_CODES[variant % COLOR_CODES.length] + "-" + size + "-" + sequence(variant),
                ProductCategory.CLOTHING_TOP, "Synthetic " + family[0] + " " + family[1]
                        + " in " + size + " and " + color + ".", specs);
    }

    private Product clothingBottom(int variant) {
        String[][] families = {
                {"Levi's", "501 Original Fit Jeans", "00501"},
                {"Levi's", "511 Slim Fit Jeans", "04511"},
                {"Nike", "Sportswear Club Fleece Joggers", "BV2671"},
                {"Adidas", "Essentials 3-Stripes Pants", "IC6748"},
                {"Under Armour", "Rival Fleece Joggers", "1379774"},
                {"Lululemon", "ABC Classic-Fit Trouser", "ABC"},
                {"Gap", "Straight Fit Jeans", "GAP"},
                {"Levi's", "502 Taper Jeans", "29507"}
        };
        String[] family = families[variant % families.length];
        int waist = 28 + (variant / families.length) % 13;
        int inseam = new int[]{28, 30, 32, 34}[(variant / (families.length * 2)) % 4];
        String size = waist + "x" + inseam;
        String color = variant % 2 == 0 ? "dark blue" : "black";
        Map<String, Object> specs = Map.of(
                "garmentType", family[1].toLowerCase().contains("jeans") ? "jeans" : "pants",
                "size", size,
                "sizeSystem", "US",
                "waistInches", waist,
                "inseamInches", inseam,
                "fit", family[1].toLowerCase().contains("slim") ? "slim" : "straight",
                "material", family[1].toLowerCase().contains("jeans") ? "cotton denim" : "cotton blend",
                "color", color
        );
        return product(family[0] + " " + family[1] + " " + size, family[0],
                family[2] + "-" + size + "-" + sequence(variant),
                ProductCategory.CLOTHING_BOTTOM, "Synthetic " + family[0] + " " + family[1]
                        + " in size " + size + ".", specs);
    }

    private Product footwear(int variant) {
        String[][] families = {
                {"Adidas", "Ultraboost 5", "JH9634"},
                {"Nike", "Pegasus 41", "FD2722"},
                {"New Balance", "574 Core", "ML574"},
                {"ASICS", "Gel-Kayano 31", "1011B867"},
                {"Brooks", "Ghost 16", "110418"},
                {"Hoka", "Clifton 9", "1127895"},
                {"Puma", "Velocity Nitro 3", "377748"},
                {"Adidas", "Duramo SL 2.0", "IF9390"}
        };
        String[] family = families[variant % families.length];
        int size = 6 + (variant / families.length) % 10;
        Map<String, Object> specs = Map.of(
                "shoeType", family[1].toLowerCase().contains("running") ? "running shoe" : "running shoe",
                "size", size,
                "sizeSystem", "US",
                "width", variant % 5 == 0 ? "wide" : "medium",
                "material", "engineered mesh",
                "closure", "lace",
                "intendedUse", variant % 4 == 0 ? "road running" : "everyday wear"
        );
        return product(family[0] + " " + family[1] + " Running Shoes US " + size, family[0],
                family[2] + "-" + size + "US-" + sequence(variant),
                ProductCategory.FOOTWEAR, "Synthetic " + family[0] + " " + family[1]
                        + " footwear in US size " + size + ".", specs);
    }

    private Product product(
            String name,
            String brand,
            String model,
            ProductCategory category,
            String description,
            Map<String, Object> attributes
    ) {
        return new Product(name, brand, model, category, description, null, attributes);
    }

    private Retailer secondRetailer(
            Product product,
            Retailer bestBuy,
            Retailer appleStore,
            Retailer sportChek,
            Retailer nikeCanada,
            Retailer levisCanada
    ) {
        return switch (product.getCategory()) {
            case LAPTOP, TABLET -> product.getBrand().equals("Apple") ? appleStore : bestBuy;
            case SMARTPHONE, TELEVISION, HEADPHONES -> bestBuy;
            case CLOTHING_TOP -> product.getBrand().equals("Nike") ? nikeCanada : sportChek;
            case CLOTHING_BOTTOM -> product.getBrand().equals("Levi's") ? levisCanada : sportChek;
            case FOOTWEAR -> sportChek;
        };
    }

    private Retailer retailer(String name) {
        return retailerRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> retailerRepository.save(new Retailer(name)));
    }

    private Review review(Product product, Retailer retailer, int seed, String store) {
        return new Review(
                product,
                retailer,
                BigDecimal.valueOf(3.5 + (seed % 4) * 0.5),
                "Synthetic catalog review for " + product.getName() + " from " + store + ".",
                OffsetDateTime.now().minusDays(seed % 180)
        );
    }

    private BigDecimal price(ProductCategory category, int seed) {
        int min = switch (category) {
            case SMARTPHONE -> 500;
            case LAPTOP -> 700;
            case TABLET -> 250;
            case TELEVISION -> 350;
            case HEADPHONES -> 50;
            case CLOTHING_TOP -> 20;
            case CLOTHING_BOTTOM -> 40;
            case FOOTWEAR -> 60;
        };
        int width = switch (category) {
            case SMARTPHONE -> 1300;
            case LAPTOP -> 3300;
            case TABLET -> 1800;
            case TELEVISION -> 5700;
            case HEADPHONES -> 750;
            case CLOTHING_TOP -> 180;
            case CLOTHING_BOTTOM -> 260;
            case FOOTWEAR -> 340;
        };
        return BigDecimal.valueOf(min + Math.floorMod(seed * 37, width))
                .add(new BigDecimal("0.99"));
    }

    private String sequence(int value) {
        return String.format("%04d", value + 1);
    }
}
