package com.example.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * App-wide translations.
 *
 * The screens are one enormous composable tree, so passing a language argument down through every
 * function was never going to happen — [LocalAppLanguage] carries it instead. Any composable can
 * call [t] with no plumbing at all, and switching language recomposes the whole tree because the
 * CompositionLocal is read wherever text is drawn.
 *
 * Kept as a Kotlin map rather than `res/values-hi/strings.xml` on purpose: the language is an
 * in-app preference stored with the garage session, not the device locale, so it must be able to
 * change without recreating the Activity or fighting the system locale.
 */
val LocalAppLanguage = compositionLocalOf { "en" }

/** Look up [key] in the active language, falling back to English and finally the key itself. */
@Composable
@ReadOnlyComposable
fun t(key: String): String = Strings.get(key, LocalAppLanguage.current)

/** Non-composable lookup for callbacks, toasts and generated documents. */
fun tr(key: String, lang: String): String = Strings.get(key, lang)

object Strings {

    fun get(key: String, lang: String): String {
        val table = when (lang) {
            "hi" -> hi
            "mr" -> mr
            else -> en
        }
        return table[key] ?: en[key] ?: key
    }

    /** Every key that exists, so a coverage check can spot gaps. */
    fun keys(): Set<String> = en.keys

    /** Keys missing from a translation table. */
    fun missingIn(lang: String): Set<String> = when (lang) {
        "hi" -> en.keys - hi.keys
        "mr" -> en.keys - mr.keys
        else -> emptySet()
    }

    private val en = mapOf(
        // Navigation
        "nav.home" to "Home",
        "nav.jobs" to "Jobs",
        "nav.stock" to "Stock",
        "nav.more" to "More",
        "nav.clients" to "Clients",
        "nav.cash" to "Cash",
        "nav.reports" to "Reports",

        // Common actions
        "action.save" to "Save",
        "action.cancel" to "Cancel",
        "action.add" to "Add",
        "action.delete" to "Delete",
        "action.edit" to "Edit",
        "action.close" to "Close",
        "action.back" to "Back",
        "action.continue" to "Continue",
        "action.share" to "Share",
        "action.print" to "Print",
        "action.viewAll" to "VIEW ALL",
        "action.logout" to "Log Out of Garage",

        // Dashboard
        "dash.newJob" to "NEW JOB",
        "dash.billing" to "BILLING",
        "dash.active" to "ACTIVE",
        "dash.done" to "DONE",
        "dash.closed" to "CLOSED",
        "dash.stock" to "STOCK",
        "dash.cash" to "CASH",
        "dash.reports" to "REPORTS",
        "dash.clients" to "CLIENTS",
        "dash.servicePlans" to "SERVICE PLANS & MEMBERSHIPS",
        "dash.plan" to "Plan",
        "dash.activeSubs" to "ACTIVE SUBS",
        "dash.totalCustomers" to "TOTAL CUSTOMERS",
        "dash.expiringSoon" to "EXPIRING SOON",
        "dash.next3Days" to "NEXT 3 DAYS",
        "dash.todaysLoad" to "TODAY'S LOAD",
        "dash.scheduled" to "SCHEDULED",
        "dash.subRevenue" to "SUB REVENUE",
        "dash.thisMonth" to "THIS MONTH",
        "dash.totalRevenue" to "TOTAL REVENUE",
        "dash.combinedIncome" to "COMBINED INCOME",
        "dash.lowStock" to "LOW STOCK ALERT",
        "dash.recentJobs" to "RECENT JOBS",
        "dash.restricted" to "Restricted to Manager / Owner",

        // Jobs
        "jobs.title" to "Jobs",
        "jobs.open" to "Open",
        "jobs.completed" to "Completed",
        "jobs.closedTab" to "Closed",
        "jobs.all" to "All Jobs",
        "jobs.none" to "No job cards found.",
        "jobs.createHint" to "Click the + button to create a new job card.",
        "jobs.items" to "Items",

        // Job card form
        "job.newTitle" to "New Job Card",
        "job.step" to "Step",
        "job.of2" to "of 2",
        "job.vehicleOwner" to "Vehicle & Owner Particulars",
        "job.tasksBilling" to "Tasks, Spares & Billing",
        "job.vehicleInfo" to "VEHICLE INFORMATION",
        "job.vehicleType" to "Vehicle Type *",
        "job.plate" to "Plate Number *",
        "job.make" to "Make",
        "job.model" to "Model",
        "job.variant" to "Variant",
        "job.km" to "KM Reading",
        "job.fuel" to "Fuel Level %",
        "job.customerDetails" to "CUSTOMER OWNER DETAILS",
        "job.fullName" to "Full Name *",
        "job.phone" to "Phone Number *",
        "job.complaints" to "JOB COMPLAINTS",
        "job.symptoms" to "Description of Symptoms",
        "job.workMaterials" to "WORK & MATERIALS RECORD",
        "job.tasksDone" to "Completed Maintenance Tasks",
        "job.materialsUsed" to "Materials/Parts Used",
        "job.billingDetails" to "BILLING DETAILS",
        "job.labour" to "Labor Charges (₹)",
        "job.spares" to "Spares Cost (₹)",
        "job.save" to "Save Job Card",
        "job.selectTypeFirst" to "Please select the Vehicle Type first",
        "job.fillRequired" to "Please fill out all required fields marked with *",
        "job.validPhone" to "Please enter a valid 10-digit Phone Number",

        // Stock
        "stock.title" to "Stock",
        "stock.add" to "Stock",
        "stock.addItem" to "Add Stock Item",
        "stock.editItem" to "Edit Stock Item",
        "stock.type" to "Stock Type *",
        "stock.name" to "Part / Item Name",
        "stock.qty" to "Quantity",
        "stock.unitPrice" to "Unit Price (₹)",
        "stock.alertLevel" to "Low Stock Alert Threshold",
        "stock.addBtn" to "Add Stock",
        "stock.saveBtn" to "Save Changes",
        "stock.none" to "No stock items in catalog.",
        "stock.addFirst" to "+ Add First Item",
        "stock.alertUnder" to "Alert under",

        // Cashbook
        "cash.title" to "Cashbook Ledger",
        "cash.subtitle" to "Track garage income, expenses & daily cashflow",
        "cash.netBalance" to "NET CASH BALANCE",
        "cash.surplus" to "Surplus",
        "cash.deficit" to "Deficit",
        "cash.logEntry" to "Log Entry",
        "cash.income" to "INCOME",
        "cash.expense" to "EXPENSE",

        // Customers
        "cust.title" to "Customer Management",
        "cust.profiles" to "Client Profiles",
        "cust.memberships" to "Wash Memberships",
        "cust.none" to "No customer profiles found. Tap + to add one.",
        "cust.noMemberships" to "No wash subscriptions registered.",

        // Reports
        "rep.title" to "Reports",
        "rep.subtitle" to "Live from your Garage Guru cloud data",
        "rep.syncing" to "Syncing with cloud…",
        "rep.today" to "Today",
        "rep.week" to "7 Days",
        "rep.month" to "This Month",
        "rep.allTime" to "All Time",
        "rep.netFor" to "NET FOR",
        "rep.jobCards" to "JOB CARDS",
        "rep.created" to "CREATED",
        "rep.byPaymentMode" to "INCOME BY PAYMENT MODE",
        "rep.byCategory" to "INCOME BY CATEGORY",
        "rep.topCustomers" to "TOP CUSTOMERS",
        "rep.stockSubs" to "STOCK & SUBSCRIPTIONS",
        "rep.totalInvoiced" to "Total invoiced",
        "rep.labour" to "Labour charges",
        "rep.parts" to "Spares / parts",
        "rep.tax" to "Tax collected",
        "rep.stockValue" to "Stock on hand (value)",
        "rep.belowAlert" to "Items below alert level",
        "rep.activeMemberships" to "Active memberships",
        "rep.duesPending" to "Membership dues pending",
        "rep.noActivity" to "No activity in this period",

        // Invoice
        "inv.billTo" to "BILL TO",
        "inv.vehicleInfo" to "VEHICLE INFO",
        "inv.itemized" to "ITEMIZED BILLING",
        "inv.addItem" to "Add Service / Spare / Lube",
        "inv.noItems" to "No services or materials added yet. Click + Add button above to insert items.",
        "inv.terms" to "TERMS & CONDITIONS:",
        "inv.complete" to "Complete",
        "inv.paidClose" to "Paid & Close",
        "inv.shareSms" to "Share SMS Invoice",
        "inv.convert" to "Convert to Job Card",
        "inv.exportPdf" to "Export PDF",

        // Settings
        "set.title" to "Account & Settings",
        "set.garageDetails" to "GARAGE DETAILS & LOGO",
        "set.verified" to "VERIFIED WORKSHOP",
        "set.uploadLogo" to "Upload Garage Logo Image",
        "set.changeLogo" to "Change Garage Logo Image",
        "set.customLogoActive" to "Custom logo active",
        "set.uploadHint" to "Tap icon or button to upload custom logo",
        "set.ownerAdmin" to "Garage Owner & Admin",
        "set.preferences" to "APP PREFERENCES & LANGUAGE",
        "set.modules" to "WORKSHOP MODULES",
        "set.team" to "Garage Team & Mechanics",
        "set.teamSub" to "Manage Mechanics, Technicians & Roles",
        "set.stockModule" to "Stock Inventory",
        "set.clientsModule" to "Clients & Wash",
        "set.ledgerModule" to "Ledger Book",
        "set.alertsModule" to "Service Alerts",
        "set.system" to "PREFERENCES & SYSTEM",
        "set.language" to "App Language",
        "set.dbSync" to "Database Sync",
        "set.online" to "Online & Synced",
        "set.version" to "Garage Guru Version",

        // Team
        "team.title" to "Garage Team & Roles",
        "team.add" to "Add Member",
        "team.credentials" to "LOGIN CREDENTIALS",
        "team.username" to "Username",
        "team.password" to "Password",
        "team.name" to "Member Name",
        "team.mobile" to "Mobile Number",

        // Reminders
        "rem.title" to "Service Alerts & Reminders",
        "rem.newAlert" to "New Interval Alert",
        "rem.activeDue" to "Active Intervals Due",
        "rem.none" to "No upcoming service alerts registered.",
        "rem.newTitle" to "New Service Interval Reminder",
        "rem.customer" to "Customer *",
        "rem.customerPhone" to "Customer Phone",
        "rem.plate" to "Vehicle Plate",
        "rem.serviceType" to "Service Type",
        "rem.intervalKm" to "Interval KM",
        "rem.dueDays" to "Due in Days",
        "rem.notes" to "Reminder Notes",
        "rem.save" to "Save Service Alert",
        "rem.noCustomers" to "No customers yet — add a customer or job card first.",

        // Offline
        "offline.banner" to "OFFLINE - working locally"
    )

    private val hi = mapOf(
        "nav.home" to "होम", "nav.jobs" to "जॉब", "nav.stock" to "स्टॉक", "nav.more" to "और",
        "nav.clients" to "ग्राहक", "nav.cash" to "कैश", "nav.reports" to "रिपोर्ट",

        "action.save" to "सेव करें", "action.cancel" to "रद्द करें", "action.add" to "जोड़ें",
        "action.delete" to "हटाएं", "action.edit" to "संपादित करें", "action.close" to "बंद करें",
        "action.back" to "पीछे", "action.continue" to "आगे बढ़ें", "action.share" to "साझा करें",
        "action.print" to "प्रिंट", "action.viewAll" to "सभी देखें", "action.logout" to "गैराज से लॉग आउट",

        "dash.newJob" to "नया जॉब", "dash.billing" to "बिलिंग", "dash.active" to "सक्रिय",
        "dash.done" to "पूर्ण", "dash.closed" to "बंद", "dash.stock" to "स्टॉक", "dash.cash" to "कैश",
        "dash.reports" to "रिपोर्ट", "dash.clients" to "ग्राहक",
        "dash.servicePlans" to "सर्विस प्लान और सदस्यता", "dash.plan" to "प्लान",
        "dash.activeSubs" to "सक्रिय सदस्य", "dash.totalCustomers" to "कुल ग्राहक",
        "dash.expiringSoon" to "जल्द समाप्त", "dash.next3Days" to "अगले 3 दिन",
        "dash.todaysLoad" to "आज का काम", "dash.scheduled" to "निर्धारित",
        "dash.subRevenue" to "सदस्यता आय", "dash.thisMonth" to "इस महीने",
        "dash.totalRevenue" to "कुल आय", "dash.combinedIncome" to "संयुक्त आय",
        "dash.lowStock" to "कम स्टॉक चेतावनी", "dash.recentJobs" to "हाल के जॉब",
        "dash.restricted" to "केवल मैनेजर / मालिक के लिए",

        "jobs.title" to "जॉब", "jobs.open" to "खुले", "jobs.completed" to "पूर्ण",
        "jobs.closedTab" to "बंद", "jobs.all" to "सभी जॉब", "jobs.none" to "कोई जॉब कार्ड नहीं मिला।",
        "jobs.createHint" to "नया जॉब कार्ड बनाने के लिए + दबाएं।", "jobs.items" to "आइटम",

        "job.newTitle" to "नया जॉब कार्ड", "job.step" to "चरण", "job.of2" to "में से 2",
        "job.vehicleOwner" to "वाहन और मालिक विवरण", "job.tasksBilling" to "काम, पार्ट्स और बिलिंग",
        "job.vehicleInfo" to "वाहन की जानकारी", "job.vehicleType" to "वाहन प्रकार *",
        "job.plate" to "नंबर प्लेट *", "job.make" to "कंपनी", "job.model" to "मॉडल",
        "job.variant" to "वेरिएंट", "job.km" to "किलोमीटर", "job.fuel" to "ईंधन %",
        "job.customerDetails" to "ग्राहक विवरण", "job.fullName" to "पूरा नाम *",
        "job.phone" to "फोन नंबर *", "job.complaints" to "शिकायतें",
        "job.symptoms" to "समस्या का विवरण", "job.workMaterials" to "काम और सामग्री",
        "job.tasksDone" to "पूर्ण किए गए काम", "job.materialsUsed" to "उपयोग किए गए पार्ट्स",
        "job.billingDetails" to "बिलिंग विवरण", "job.labour" to "मजदूरी (₹)",
        "job.spares" to "पार्ट्स लागत (₹)", "job.save" to "जॉब कार्ड सेव करें",
        "job.selectTypeFirst" to "कृपया पहले वाहन प्रकार चुनें",
        "job.fillRequired" to "कृपया * वाले सभी फ़ील्ड भरें",
        "job.validPhone" to "कृपया सही 10 अंकों का फोन नंबर डालें",

        "stock.title" to "स्टॉक", "stock.add" to "स्टॉक", "stock.addItem" to "स्टॉक आइटम जोड़ें",
        "stock.editItem" to "स्टॉक आइटम संपादित करें", "stock.type" to "स्टॉक प्रकार *",
        "stock.name" to "पार्ट / आइटम नाम", "stock.qty" to "मात्रा", "stock.unitPrice" to "प्रति यूनिट मूल्य (₹)",
        "stock.alertLevel" to "कम स्टॉक चेतावनी सीमा", "stock.addBtn" to "स्टॉक जोड़ें",
        "stock.saveBtn" to "बदलाव सेव करें", "stock.none" to "कैटलॉग में कोई स्टॉक नहीं।",
        "stock.addFirst" to "+ पहला आइटम जोड़ें", "stock.alertUnder" to "चेतावनी सीमा",

        "cash.title" to "कैशबुक", "cash.subtitle" to "गैराज की आय, खर्च और नकदी प्रवाह",
        "cash.netBalance" to "शुद्ध नकद शेष", "cash.surplus" to "बचत", "cash.deficit" to "घाटा",
        "cash.logEntry" to "एंट्री दर्ज करें", "cash.income" to "आय", "cash.expense" to "खर्च",

        "cust.title" to "ग्राहक प्रबंधन", "cust.profiles" to "ग्राहक प्रोफ़ाइल",
        "cust.memberships" to "वॉश सदस्यता", "cust.none" to "कोई ग्राहक नहीं। + दबाकर जोड़ें।",
        "cust.noMemberships" to "कोई वॉश सदस्यता पंजीकृत नहीं।",

        "rep.title" to "रिपोर्ट", "rep.subtitle" to "आपके गैराज गुरु क्लाउड डेटा से",
        "rep.syncing" to "क्लाउड से सिंक हो रहा है…", "rep.today" to "आज", "rep.week" to "7 दिन",
        "rep.month" to "इस महीने", "rep.allTime" to "सभी समय", "rep.netFor" to "शुद्ध",
        "rep.jobCards" to "जॉब कार्ड", "rep.created" to "बनाए",
        "rep.byPaymentMode" to "भुगतान माध्यम अनुसार आय", "rep.byCategory" to "श्रेणी अनुसार आय",
        "rep.topCustomers" to "प्रमुख ग्राहक", "rep.stockSubs" to "स्टॉक और सदस्यता",
        "rep.totalInvoiced" to "कुल बिल", "rep.labour" to "मजदूरी", "rep.parts" to "पार्ट्स",
        "rep.tax" to "कर", "rep.stockValue" to "स्टॉक मूल्य", "rep.belowAlert" to "चेतावनी से नीचे आइटम",
        "rep.activeMemberships" to "सक्रिय सदस्यता", "rep.duesPending" to "बकाया राशि",
        "rep.noActivity" to "इस अवधि में कोई गतिविधि नहीं",

        "inv.billTo" to "बिल प्राप्तकर्ता", "inv.vehicleInfo" to "वाहन जानकारी",
        "inv.itemized" to "विस्तृत बिल", "inv.addItem" to "सर्विस / पार्ट / लूब जोड़ें",
        "inv.noItems" to "अभी कोई आइटम नहीं। ऊपर + दबाकर जोड़ें।", "inv.terms" to "नियम और शर्तें:",
        "inv.complete" to "पूर्ण करें", "inv.paidClose" to "भुगतान और बंद करें",
        "inv.shareSms" to "बिल साझा करें", "inv.convert" to "जॉब कार्ड में बदलें",
        "inv.exportPdf" to "पीडीएफ निर्यात",

        "set.title" to "खाता और सेटिंग्स", "set.garageDetails" to "गैराज विवरण और लोगो",
        "set.verified" to "सत्यापित वर्कशॉप", "set.uploadLogo" to "गैराज लोगो अपलोड करें",
        "set.changeLogo" to "गैराज लोगो बदलें", "set.customLogoActive" to "कस्टम लोगो सक्रिय",
        "set.uploadHint" to "लोगो अपलोड करने के लिए दबाएं", "set.ownerAdmin" to "गैराज मालिक और एडमिन",
        "set.preferences" to "ऐप प्राथमिकताएं और भाषा", "set.modules" to "वर्कशॉप मॉड्यूल",
        "set.team" to "गैराज टीम और मैकेनिक", "set.teamSub" to "मैकेनिक, तकनीशियन और भूमिकाएं",
        "set.stockModule" to "स्टॉक इन्वेंटरी", "set.clientsModule" to "ग्राहक और वॉश",
        "set.ledgerModule" to "लेजर बुक", "set.alertsModule" to "सर्विस अलर्ट",
        "set.system" to "प्राथमिकताएं और सिस्टम", "set.language" to "ऐप की भाषा",
        "set.dbSync" to "डेटाबेस सिंक", "set.online" to "ऑनलाइन और सिंक",
        "set.version" to "गैराज गुरु संस्करण",

        "team.title" to "गैराज टीम और भूमिकाएं", "team.add" to "सदस्य जोड़ें",
        "team.credentials" to "लॉगिन विवरण", "team.username" to "यूज़रनेम",
        "team.password" to "पासवर्ड", "team.name" to "सदस्य का नाम", "team.mobile" to "मोबाइल नंबर",

        "rem.title" to "सर्विस अलर्ट और रिमाइंडर", "rem.newAlert" to "नया अंतराल अलर्ट",
        "rem.activeDue" to "सक्रिय अंतराल देय", "rem.none" to "कोई आगामी सर्विस अलर्ट नहीं।",
        "rem.newTitle" to "नया सर्विस अंतराल रिमाइंडर", "rem.customer" to "ग्राहक *",
        "rem.customerPhone" to "ग्राहक फोन", "rem.plate" to "वाहन नंबर",
        "rem.serviceType" to "सर्विस प्रकार", "rem.intervalKm" to "अंतराल किमी",
        "rem.dueDays" to "कितने दिनों में", "rem.notes" to "रिमाइंडर नोट्स",
        "rem.save" to "सर्विस अलर्ट सेव करें",
        "rem.noCustomers" to "अभी कोई ग्राहक नहीं — पहले ग्राहक या जॉब कार्ड जोड़ें।",

        "offline.banner" to "ऑफलाइन - स्थानीय रूप से काम कर रहा है"
    )

    private val mr = mapOf(
        "nav.home" to "होम", "nav.jobs" to "जॉब", "nav.stock" to "साठा", "nav.more" to "अधिक",
        "nav.clients" to "ग्राहक", "nav.cash" to "रोख", "nav.reports" to "अहवाल",

        "action.save" to "जतन करा", "action.cancel" to "रद्द करा", "action.add" to "जोडा",
        "action.delete" to "काढा", "action.edit" to "संपादित करा", "action.close" to "बंद करा",
        "action.back" to "मागे", "action.continue" to "पुढे चला", "action.share" to "शेअर करा",
        "action.print" to "प्रिंट", "action.viewAll" to "सर्व पहा", "action.logout" to "गॅरेजमधून लॉग आउट",

        "dash.newJob" to "नवीन जॉब", "dash.billing" to "बिलिंग", "dash.active" to "सुरू",
        "dash.done" to "पूर्ण", "dash.closed" to "बंद", "dash.stock" to "साठा", "dash.cash" to "रोख",
        "dash.reports" to "अहवाल", "dash.clients" to "ग्राहक",
        "dash.servicePlans" to "सर्व्हिस प्लॅन आणि सदस्यत्व", "dash.plan" to "प्लॅन",
        "dash.activeSubs" to "सक्रिय सदस्य", "dash.totalCustomers" to "एकूण ग्राहक",
        "dash.expiringSoon" to "लवकर संपणारे", "dash.next3Days" to "पुढील 3 दिवस",
        "dash.todaysLoad" to "आजचे काम", "dash.scheduled" to "नियोजित",
        "dash.subRevenue" to "सदस्यत्व उत्पन्न", "dash.thisMonth" to "या महिन्यात",
        "dash.totalRevenue" to "एकूण उत्पन्न", "dash.combinedIncome" to "एकत्रित उत्पन्न",
        "dash.lowStock" to "कमी साठा चेतावनी", "dash.recentJobs" to "अलीकडील जॉब",
        "dash.restricted" to "फक्त व्यवस्थापक / मालकांसाठी",

        "jobs.title" to "जॉब", "jobs.open" to "सुरू", "jobs.completed" to "पूर्ण",
        "jobs.closedTab" to "बंद", "jobs.all" to "सर्व जॉब", "jobs.none" to "कोणतेही जॉब कार्ड सापडले नाही.",
        "jobs.createHint" to "नवीन जॉब कार्डसाठी + दाबा.", "jobs.items" to "वस्तू",

        "job.newTitle" to "नवीन जॉब कार्ड", "job.step" to "टप्पा", "job.of2" to "पैकी 2",
        "job.vehicleOwner" to "वाहन आणि मालक तपशील", "job.tasksBilling" to "काम, पार्ट्स आणि बिलिंग",
        "job.vehicleInfo" to "वाहनाची माहिती", "job.vehicleType" to "वाहन प्रकार *",
        "job.plate" to "नंबर प्लेट *", "job.make" to "कंपनी", "job.model" to "मॉडेल",
        "job.variant" to "व्हेरिएंट", "job.km" to "किलोमीटर", "job.fuel" to "इंधन %",
        "job.customerDetails" to "ग्राहक तपशील", "job.fullName" to "पूर्ण नाव *",
        "job.phone" to "फोन नंबर *", "job.complaints" to "तक्रारी",
        "job.symptoms" to "समस्येचे वर्णन", "job.workMaterials" to "काम आणि साहित्य",
        "job.tasksDone" to "पूर्ण झालेली कामे", "job.materialsUsed" to "वापरलेले पार्ट्स",
        "job.billingDetails" to "बिलिंग तपशील", "job.labour" to "मजुरी (₹)",
        "job.spares" to "पार्ट्स खर्च (₹)", "job.save" to "जॉब कार्ड जतन करा",
        "job.selectTypeFirst" to "कृपया आधी वाहन प्रकार निवडा",
        "job.fillRequired" to "कृपया * असलेली सर्व माहिती भरा",
        "job.validPhone" to "कृपया योग्य 10 अंकी फोन नंबर टाका",

        "stock.title" to "साठा", "stock.add" to "साठा", "stock.addItem" to "साठा वस्तू जोडा",
        "stock.editItem" to "साठा वस्तू संपादित करा", "stock.type" to "साठा प्रकार *",
        "stock.name" to "पार्ट / वस्तूचे नाव", "stock.qty" to "प्रमाण", "stock.unitPrice" to "प्रति नग किंमत (₹)",
        "stock.alertLevel" to "कमी साठा चेतावनी मर्यादा", "stock.addBtn" to "साठा जोडा",
        "stock.saveBtn" to "बदल जतन करा", "stock.none" to "कॅटलॉगमध्ये साठा नाही.",
        "stock.addFirst" to "+ पहिली वस्तू जोडा", "stock.alertUnder" to "चेतावनी मर्यादा",

        "cash.title" to "रोखवही", "cash.subtitle" to "गॅरेजचे उत्पन्न, खर्च आणि रोख प्रवाह",
        "cash.netBalance" to "निव्वळ रोख शिल्लक", "cash.surplus" to "शिल्लक", "cash.deficit" to "तूट",
        "cash.logEntry" to "नोंद करा", "cash.income" to "उत्पन्न", "cash.expense" to "खर्च",

        "cust.title" to "ग्राहक व्यवस्थापन", "cust.profiles" to "ग्राहक प्रोफाइल",
        "cust.memberships" to "वॉश सदस्यत्व", "cust.none" to "ग्राहक नाहीत. + दाबून जोडा.",
        "cust.noMemberships" to "कोणतेही वॉश सदस्यत्व नोंदलेले नाही.",

        "rep.title" to "अहवाल", "rep.subtitle" to "तुमच्या गॅरेज गुरु क्लाउड डेटामधून",
        "rep.syncing" to "क्लाउडशी सिंक होत आहे…", "rep.today" to "आज", "rep.week" to "7 दिवस",
        "rep.month" to "या महिन्यात", "rep.allTime" to "सर्व काळ", "rep.netFor" to "निव्वळ",
        "rep.jobCards" to "जॉब कार्ड", "rep.created" to "तयार",
        "rep.byPaymentMode" to "पेमेंट प्रकारानुसार उत्पन्न", "rep.byCategory" to "श्रेणीनुसार उत्पन्न",
        "rep.topCustomers" to "प्रमुख ग्राहक", "rep.stockSubs" to "साठा आणि सदस्यत्व",
        "rep.totalInvoiced" to "एकूण बिल", "rep.labour" to "मजुरी", "rep.parts" to "पार्ट्स",
        "rep.tax" to "कर", "rep.stockValue" to "साठ्याचे मूल्य", "rep.belowAlert" to "चेतावनीखालील वस्तू",
        "rep.activeMemberships" to "सक्रिय सदस्यत्वे", "rep.duesPending" to "थकबाकी",
        "rep.noActivity" to "या कालावधीत कोणतीही हालचाल नाही",

        "inv.billTo" to "बिल घेणारा", "inv.vehicleInfo" to "वाहन माहिती",
        "inv.itemized" to "तपशीलवार बिल", "inv.addItem" to "सर्व्हिस / पार्ट / लूब जोडा",
        "inv.noItems" to "अजून कोणतीही वस्तू नाही. वर + दाबून जोडा.", "inv.terms" to "अटी आणि शर्ती:",
        "inv.complete" to "पूर्ण करा", "inv.paidClose" to "पेमेंट आणि बंद करा",
        "inv.shareSms" to "बिल शेअर करा", "inv.convert" to "जॉब कार्डमध्ये बदला",
        "inv.exportPdf" to "पीडीएफ निर्यात",

        "set.title" to "खाते आणि सेटिंग्ज", "set.garageDetails" to "गॅरेज तपशील आणि लोगो",
        "set.verified" to "सत्यापित वर्कशॉप", "set.uploadLogo" to "गॅरेज लोगो अपलोड करा",
        "set.changeLogo" to "गॅरेज लोगो बदला", "set.customLogoActive" to "कस्टम लोगो सक्रिय",
        "set.uploadHint" to "लोगो अपलोड करण्यासाठी दाबा", "set.ownerAdmin" to "गॅरेज मालक आणि ॲडमिन",
        "set.preferences" to "ॲप प्राधान्ये आणि भाषा", "set.modules" to "वर्कशॉप मॉड्यूल",
        "set.team" to "गॅरेज टीम आणि मेकॅनिक", "set.teamSub" to "मेकॅनिक, तंत्रज्ञ आणि भूमिका",
        "set.stockModule" to "साठा यादी", "set.clientsModule" to "ग्राहक आणि वॉश",
        "set.ledgerModule" to "लेजर बुक", "set.alertsModule" to "सर्व्हिस अलर्ट",
        "set.system" to "प्राधान्ये आणि सिस्टीम", "set.language" to "ॲपची भाषा",
        "set.dbSync" to "डेटाबेस सिंक", "set.online" to "ऑनलाइन आणि सिंक",
        "set.version" to "गॅरेज गुरु आवृत्ती",

        "team.title" to "गॅरेज टीम आणि भूमिका", "team.add" to "सदस्य जोडा",
        "team.credentials" to "लॉगिन तपशील", "team.username" to "यूजरनेम",
        "team.password" to "पासवर्ड", "team.name" to "सदस्याचे नाव", "team.mobile" to "मोबाईल नंबर",

        "rem.title" to "सर्व्हिस अलर्ट आणि स्मरणपत्रे", "rem.newAlert" to "नवीन अंतराल अलर्ट",
        "rem.activeDue" to "सक्रिय अंतराल देय", "rem.none" to "आगामी सर्व्हिस अलर्ट नाहीत.",
        "rem.newTitle" to "नवीन सर्व्हिस अंतराल स्मरणपत्र", "rem.customer" to "ग्राहक *",
        "rem.customerPhone" to "ग्राहक फोन", "rem.plate" to "वाहन नंबर",
        "rem.serviceType" to "सर्व्हिस प्रकार", "rem.intervalKm" to "अंतराल किमी",
        "rem.dueDays" to "किती दिवसांत", "rem.notes" to "स्मरणपत्र नोंदी",
        "rem.save" to "सर्व्हिस अलर्ट जतन करा",
        "rem.noCustomers" to "अजून ग्राहक नाहीत — आधी ग्राहक किंवा जॉब कार्ड जोडा.",

        "offline.banner" to "ऑफलाइन - स्थानिक पातळीवर काम सुरू आहे"
    )
}
