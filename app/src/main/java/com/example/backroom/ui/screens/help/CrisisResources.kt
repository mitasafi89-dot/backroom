package com.example.backroom.ui.screens.help

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Crisis Resource Categories
 */
enum class ResourceCategory(val displayName: String, val displayNameSw: String) {
    CRISIS("Crisis Support", "Msaada wa Dharura"),
    MENTAL_HEALTH("Mental Health", "Afya ya Akili"),
    GENDER_VIOLENCE("Gender-Based Violence", "Ukatili wa Kijinsia"),
    SUBSTANCE_ABUSE("Substance Abuse", "Madawa ya Kulevya"),
    YOUTH_SUPPORT("Youth Support", "Msaada wa Vijana"),
    LEGAL_AID("Legal Aid", "Msaada wa Kisheria")
}

/**
 * A crisis or help resource
 */
data class CrisisResource(
    val id: String,
    val name: String,
    val nameSw: String,
    val phone: String,
    val description: String,
    val descriptionSw: String,
    val hours: String,
    val hoursSw: String,
    val category: ResourceCategory,
    val tier: Int, // 1 = Primary, 2 = Specialized, 3 = Online
    val isFree: Boolean = true,
    val isConfidential: Boolean = true,
    val website: String? = null
)

/**
 * Kenya Crisis Resources Database
 * Verified resources for mental health and crisis support in Kenya
 */
object KenyaCrisisResources {

    // ============================================
    // TIER 1: PRIMARY RESOURCES (Always Visible)
    // ============================================

    val befriendersKenya = CrisisResource(
        id = "befrienders_kenya",
        name = "Befrienders Kenya",
        nameSw = "Befrienders Kenya",
        phone = "+254722178177",
        description = "Free, confidential emotional support and suicide prevention",
        descriptionSw = "Msaada wa kihisia bila malipo na siri - kuzuia kujiua",
        hours = "24/7",
        hoursSw = "Masaa 24",
        category = ResourceCategory.CRISIS,
        tier = 1,
        website = "https://www.befrienders.org/kenya"
    )

    val kenyaRedCross = CrisisResource(
        id = "kenya_red_cross",
        name = "Kenya Red Cross",
        nameSw = "Msalaba Mwekundu Kenya",
        phone = "1199",
        description = "Emergency response and humanitarian services",
        descriptionSw = "Huduma za dharura na kibinadamu",
        hours = "24/7",
        hoursSw = "Masaa 24",
        category = ResourceCategory.CRISIS,
        tier = 1,
        website = "https://www.redcross.or.ke"
    )

    val mhfaKenya = CrisisResource(
        id = "mhfa_kenya",
        name = "MHFA Kenya",
        nameSw = "MHFA Kenya",
        phone = "+254722178177",
        description = "Mental Health First Aid - professional mental health support",
        descriptionSw = "Msaada wa Kwanza wa Afya ya Akili - msaada wa kitaalamu",
        hours = "Mon-Fri 8am-5pm",
        hoursSw = "Jumatatu-Ijumaa 8am-5pm",
        category = ResourceCategory.MENTAL_HEALTH,
        tier = 1,
        website = "https://mhfakenya.org"
    )

    // ============================================
    // TIER 2: SPECIALIZED RESOURCES
    // ============================================

    val fidaKenya = CrisisResource(
        id = "fida_kenya",
        name = "FIDA Kenya",
        nameSw = "FIDA Kenya",
        phone = "+254202324422",
        description = "Legal aid for women and children facing violence",
        descriptionSw = "Msaada wa kisheria kwa wanawake na watoto wanaokabiliwa na ukatili",
        hours = "Mon-Fri 8am-5pm",
        hoursSw = "Jumatatu-Ijumaa 8am-5pm",
        category = ResourceCategory.GENDER_VIOLENCE,
        tier = 2,
        website = "https://fidakenya.org"
    )

    val gvrc = CrisisResource(
        id = "gvrc",
        name = "Gender Violence Recovery Centre",
        nameSw = "Kituo cha Kupona Ukatili wa Kijinsia",
        phone = "+254202731744",
        description = "Medical care and counseling for survivors of gender violence",
        descriptionSw = "Huduma za matibabu na ushauri kwa walionusurika ukatili wa kijinsia",
        hours = "24/7",
        hoursSw = "Masaa 24",
        category = ResourceCategory.GENDER_VIOLENCE,
        tier = 2,
        website = "https://gfrcentre.org"
    )

    val childlineKenya = CrisisResource(
        id = "childline_kenya",
        name = "Childline Kenya",
        nameSw = "Childline Kenya",
        phone = "116",
        description = "Free helpline for children and young people in distress",
        descriptionSw = "Simu ya bure kwa watoto na vijana walio katika shida",
        hours = "24/7",
        hoursSw = "Masaa 24",
        category = ResourceCategory.YOUTH_SUPPORT,
        tier = 2,
        isFree = true
    )

    val nacada = CrisisResource(
        id = "nacada",
        name = "NACADA",
        nameSw = "NACADA",
        phone = "1192",
        description = "National Authority for drugs and substance abuse prevention",
        descriptionSw = "Mamlaka ya Kitaifa ya kuzuia madawa ya kulevya",
        hours = "Mon-Fri 8am-5pm",
        hoursSw = "Jumatatu-Ijumaa 8am-5pm",
        category = ResourceCategory.SUBSTANCE_ABUSE,
        tier = 2,
        website = "https://nacada.go.ke"
    )

    val mathareHospital = CrisisResource(
        id = "mathare_hospital",
        name = "Mathare National Teaching & Referral Hospital",
        nameSw = "Hospitali ya Kitaifa ya Mathare",
        phone = "+254202630493",
        description = "Public mental health hospital - psychiatric services",
        descriptionSw = "Hospitali ya umma ya afya ya akili - huduma za akili",
        hours = "24/7",
        hoursSw = "Masaa 24",
        category = ResourceCategory.MENTAL_HEALTH,
        tier = 2
    )

    val kenyaLegalAid = CrisisResource(
        id = "legal_aid",
        name = "National Legal Aid Service",
        nameSw = "Huduma ya Kitaifa ya Msaada wa Kisheria",
        phone = "+254202712044",
        description = "Free legal assistance for those who cannot afford lawyers",
        descriptionSw = "Msaada wa kisheria bila malipo kwa wasio na uwezo",
        hours = "Mon-Fri 8am-5pm",
        hoursSw = "Jumatatu-Ijumaa 8am-5pm",
        category = ResourceCategory.LEGAL_AID,
        tier = 2,
        website = "https://nlas.go.ke"
    )

    // ============================================
    // GROUPED LISTS
    // ============================================

    val primaryResources = listOf(
        befriendersKenya,
        kenyaRedCross,
        mhfaKenya
    )

    val specializedResources = listOf(
        fidaKenya,
        gvrc,
        childlineKenya,
        nacada,
        mathareHospital,
        kenyaLegalAid
    )

    val allResources = primaryResources + specializedResources

    fun getResourcesByCategory(category: ResourceCategory): List<CrisisResource> {
        return allResources.filter { it.category == category }
    }

    fun getTier1Resources(): List<CrisisResource> {
        return allResources.filter { it.tier == 1 }
    }

    fun getResourceById(id: String): CrisisResource? {
        return allResources.find { it.id == id }
    }
}

/**
 * FAQ Item
 */
data class FaqItem(
    val id: String,
    val question: String,
    val questionSw: String,
    val answer: String,
    val answerSw: String,
    val category: String
)

/**
 * Frequently Asked Questions
 */
object BackroomFaq {

    val howAnonymousAmI = FaqItem(
        id = "faq_anonymous",
        question = "How anonymous am I?",
        questionSw = "Je, siri yangu inalindwa vipi?",
        answer = """
            Very anonymous. Here's what we do:
            
            • Your voice is modified in real-time so you can't be recognized
            • We never save recordings - calls are not stored anywhere
            • We don't ask for your name, email, or phone number
            • Your anonymous ID changes periodically
            • We don't share data with third parties
            • Even we can't identify you from your calls
            
            The only data we keep is your preferences (topics, settings) which isn't linked to your identity.
        """.trimIndent(),
        answerSw = """
            Siri yako inalindwa sana. Tunachofanya:
            
            • Sauti yako inabadilishwa wakati wa mazungumzo ili usitambulike
            • Hatuhifadhi mazungumzo - simu hazihifadhiwi popote
            • Hatuombi jina lako, barua pepe, au nambari ya simu
            • Kitambulisho chako cha siri kinabadilika mara kwa mara
            • Hatushiriki data na mtu mwingine
            • Hata sisi hatuwezi kukutambua kutoka kwa simu zako
        """.trimIndent(),
        category = "privacy"
    )

    val howVoiceModificationWorks = FaqItem(
        id = "faq_voice",
        question = "How does voice modification work?",
        questionSw = "Kubadilisha sauti kunafanyaje kazi?",
        answer = """
            Your voice is modified in real-time using audio processing technology:
            
            • Pitch shifting changes how high or low your voice sounds
            • Formant modification alters voice characteristics
            • The modification happens on your device before audio is sent
            • Both people in the call hear modified voices
            • You can still understand each other clearly
            • The modification cannot be reversed
            
            This protects your identity while still allowing natural conversation.
        """.trimIndent(),
        answerSw = """
            Sauti yako inabadilishwa kwa wakati halisi kwa kutumia teknolojia:
            
            • Kubadilisha tone kunabadilisha jinsi sauti yako inavyosikika
            • Kubadilisha formant kunabadilisha sifa za sauti
            • Kubadilisha kunafanyika kwenye simu yako kabla ya kutumwa
            • Watu wote wawili wanasikia sauti zilizobadilishwa
            • Bado mnaweza kuelewa vizuri
            • Kubadilisha hakuwezi kurudishwa
        """.trimIndent(),
        category = "privacy"
    )

    val whatIfIFeelUnsafe = FaqItem(
        id = "faq_unsafe",
        question = "What if I feel unsafe during a call?",
        questionSw = "Nifanyaje nikijisikia sina usalama wakati wa simu?",
        answer = """
            Your safety is our priority. You can:
            
            1. End the call immediately - tap the red phone button
            2. Use Emergency Exit - tap the Report button for instant end + report
            3. Block the person - they'll never match with you again
            4. Report them - we review all reports within 24 hours
            
            Remember:
            • You're always anonymous - they don't know who you are
            • You can leave any call at any time
            • Reporting helps keep the community safe
            • We take all reports seriously
            
            If you're in immediate danger, please contact emergency services (999) or crisis resources in the app.
        """.trimIndent(),
        answerSw = """
            Usalama wako ni muhimu kwetu. Unaweza:
            
            1. Maliza simu mara moja - bonyeza kitufe chekundu
            2. Tumia Dharura - bonyeza Report kwa kumaliza haraka na kuripoti
            3. Mzuie mtu huyo - hataweza kukupata tena
            4. Waripoti - tunakagua ripoti zote ndani ya masaa 24
            
            Kumbuka:
            • Wewe siri daima - hawajui wewe ni nani
            • Unaweza kuondoka simu wakati wowote
            • Kuripoti kunasaidia kulinda jamii
            
            Ukiwa hatarini, wasiliana na huduma za dharura (999).
        """.trimIndent(),
        category = "safety"
    )

    val howToReport = FaqItem(
        id = "faq_report",
        question = "How do I report someone?",
        questionSw = "Nawezaje kuripoti mtu?",
        answer = """
            You can report during or after a call:
            
            During a call:
            • Tap the "Report" button (⚠️ icon)
            • The call will end and you'll see the report screen
            
            After a call:
            • On the feedback screen, tap "Report a Problem"
            • Select what happened
            • Add details if you want (optional)
            • Choose to block them (recommended)
            • Submit
            
            What happens next:
            • Your report is reviewed within 24 hours
            • We may suspend the reported user
            • Repeat offenders are permanently banned
            • Your report is confidential
            
            You won't see any updates (to protect anonymity), but rest assured we take action.
        """.trimIndent(),
        answerSw = """
            Unaweza kuripoti wakati wa simu au baadaye:
            
            Wakati wa simu:
            • Bonyeza kitufe cha "Report" (🚨)
            • Simu itaisha na utaona skrini ya ripoti
            
            Baada ya simu:
            • Kwenye skrini ya maoni, bonyeza "Report a Problem"
            • Chagua kilichotokea
            • Ongeza maelezo ukitaka (hiari)
            • Chagua kumzuia (inashauriwa)
            • Tuma
            
            Kinachofuata:
            • Ripoti yako inakaguliwa ndani ya masaa 24
            • Tunaweza kumsimamisha mtuhumiwa
            • Wanaoshambulia mara kwa mara wanafukuzwa kabisa
        """.trimIndent(),
        category = "safety"
    )

    val whatAreTopics = FaqItem(
        id = "faq_topics",
        question = "What are the different topics?",
        questionSw = "Mada tofauti ni zipi?",
        answer = """
            Topics help match you with the right listener:
            
            • Confession - Something you need to get off your chest
            • Letting Out - Frustrated and need to vent
            • Advice - Need an opinion on something
            • Grief - Lost someone or something important
            • Just Talking - No specific reason, just want to connect
            • Something Hard - Difficult to put into words
            
            Choose whatever feels right. There's no wrong choice.
        """.trimIndent(),
        answerSw = """
            Mada husaidia kukupata msikilizaji sahihi:
            
            • Kukiri - Kitu unachohitaji kutoa moyoni
            • Kutoa Hasira - Una hasira na unahitaji kutoa
            • Ushauri - Unahitaji maoni kuhusu jambo
            • Huzuni - Umepoteza mtu au kitu muhimu
            • Kuzungumza Tu - Hakuna sababu maalum
            • Jambo Gumu - Vigumu kueleza kwa maneno
        """.trimIndent(),
        category = "using_app"
    )

    val howListeningWorks = FaqItem(
        id = "faq_listening",
        question = "How does listening work?",
        questionSw = "Kusikiliza kunafanyaje kazi?",
        answer = """
            As a listener, you can help others:
            
            1. Switch to Listener mode on the home screen
            2. Set your boundaries (topics you're comfortable with)
            3. Turn on availability
            4. Wait for an incoming preview
            5. Read what the person wants to talk about
            6. Accept or skip (no pressure!)
            7. Listen and be present
            
            Tips for listening:
            • You don't need to fix anything
            • Just being there helps
            • Don't give unsolicited advice
            • Be kind and non-judgmental
            • It's okay to say "I'm not sure what to say"
        """.trimIndent(),
        answerSw = """
            Kama msikilizaji, unaweza kusaidia wengine:
            
            1. Badilisha kwenda Msikilizaji kwenye skrini ya nyumbani
            2. Weka mipaka yako (mada unazokubaliana nazo)
            3. Washa upatikanaji
            4. Subiri maonyesho yanayokuja
            5. Soma anachohitaji mtu kuzungumza
            6. Kubali au ruka (hakuna shinikizo!)
            7. Sikiliza na kuwepo
        """.trimIndent(),
        category = "using_app"
    )

    val allFaqs = listOf(
        howAnonymousAmI,
        howVoiceModificationWorks,
        whatIfIFeelUnsafe,
        howToReport,
        whatAreTopics,
        howListeningWorks
    )

    fun getFaqsByCategory(category: String): List<FaqItem> {
        return allFaqs.filter { it.category == category }
    }
}

/**
 * Utility function to dial a phone number
 */
fun dialPhoneNumber(context: Context, phoneNumber: String) {
    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$phoneNumber")
    }
    context.startActivity(intent)
}

/**
 * Utility function to open a website
 */
fun openWebsite(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(url)
    }
    context.startActivity(intent)
}

