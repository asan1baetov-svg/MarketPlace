package greenecomall.finance.finik;

/**
 * Банки-получатели переводов по номеру телефона в Finik Payments Gateway: id сервиса, код и лимиты
 * одного перевода в сомах. Перенесено из MLM-системы GreenEcoMall ({@code BankConfig}).
 */
public enum FinikBank {

    BAKAI_24("cec5df87-daa0-475d-8aca-e68b9b253ad2", "4398", "Бакай24", 10, 99_999, false),
    DEMIR_BANK("e63b44d3-69b5-4335-b9f0-49faf85e9d56", "5221", "Демир Банк", 10, 50_000, false),
    OPTIMA_BANK("07dbb2ef-ff5c-413f-a2ec-5c439ec5714d", "5428", "Оптима Банк", 15, 95_000, false),
    ELDIK_BANK("0ab40b4d-a06c-42a2-ba18-15e2240f40a3", "4019", "Элдик Банк (РСК)", 101, 69_999, false),
    ELCART("b4e55a02-5daa-4cd4-874a-b812c8a3904e", "4417", "Элкарт", 20, 15_000, false),
    KICB("5f9b81a9-aef2-4027-81f3-08d8796e6c68", "4910", "KICB Банк", 5, 99_999, false),
    AIYL_BANK("f3174f11-f399-42e0-9450-b9ee15531946", "4543", "Айыл Банк", 5, 50_000, false),
    BAI_TUSHUM("61d21503-fbfb-4f6c-bae0-2582613b8630", "5523", "Бай-Тушум", 20, 69_999, false),
    SIMBANK("96be1e30-9965-4842-8a59-231512484d8d", "5475", "Simbank", 50, 69_000, false),
    TULPAR("f89ba382-74d3-4d9d-94e5-1e2125ae3814", "3982", "Карта Тулпар", 1, 90_000, false),
    MBANK("averspay-elqr-mbank", null, "MBank", 1, 99_000, true),
    KKB("51bfbad6-e668-4c79-b010-7d1534eb2616", "4425", "Кыргызкоммерцбанк", 20, 69_999, false);

    private final String serviceId;
    private final String serviceCode;
    private final String displayName;
    private final int minSom;
    private final int maxSom;
    /** MBank принимает перевод в другом формате полей ({@code transactionType}/{@code provider}). */
    private final boolean requiresTransactionType;

    FinikBank(String serviceId, String serviceCode, String displayName, int minSom, int maxSom,
              boolean requiresTransactionType) {
        this.serviceId = serviceId;
        this.serviceCode = serviceCode;
        this.displayName = displayName;
        this.minSom = minSom;
        this.maxSom = maxSom;
        this.requiresTransactionType = requiresTransactionType;
    }

    public String serviceId() {
        return serviceId;
    }

    public String serviceCode() {
        return serviceCode;
    }

    public String displayName() {
        return displayName;
    }

    public int minSom() {
        return minSom;
    }

    public int maxSom() {
        return maxSom;
    }

    public boolean requiresTransactionType() {
        return requiresTransactionType;
    }
}
