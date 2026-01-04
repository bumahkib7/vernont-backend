# CJ Advertiser ID - What Is It?

## TL;DR

**Advertiser ID** = The **Merchant/Brand ID** in CJ's system (e.g., Carl Friedrik's unique ID)

**It's NOT**:
- ❌ Your CJ account ID (CID: 7811141)
- ❌ Your company name (Neoxus)
- ❌ Optional

**It IS**:
- ✅ The brand/merchant selling products (Carl Friedrik)
- ✅ Required for product attribution
- ✅ Needed for affiliate tracking links
- ✅ Used to organize products by merchant

---

## Why Is It Required?

### 1. **Product Attribution**
Each product needs to know which merchant it belongs to:
```
Product: "Carl Friedrik Leather Briefcase"
↓
Belongs to: Carl Friedrik (Advertiser ID: XXXXX)
↓
Your affiliate link includes this ID to track commissions
```

### 2. **Affiliate Tracking**
When a customer clicks your affiliate link:
```
https://www.anrdoezrs.net/click-YOURID-ADVERTISEID?url=...
                                    ↑
                            Carl Friedrik's ID
```

Without the advertiser ID, CJ doesn't know which merchant to credit the sale to.

### 3. **Feed Organization**
- CJ organizes feeds by advertiser
- Each advertiser (Carl Friedrik, Nike, Adidas, etc.) has their own feed
- You can join multiple advertisers' programs and ingest their feeds separately

---

## How to Find Carl Friedrik's Advertiser ID

### Method 1: CJ Account Dashboard (Recommended)

1. Login to CJ: https://members.cj.com
2. Go to **Advertisers > Advertiser Lookup**
3. Search for "Carl Friedrik"
4. Click on their name
5. Look for **"Advertiser ID"** or **"Program ID"**

### Method 2: Feed URL

The advertiser ID is often in the feed URL CJ sends you:

```
https://datafeed.cj.com/1234567/Carl_Friedrik-Product_Feed-shopping.xml.zip
                        ↑↑↑↑↑↑↑
                    Advertiser ID
```

Look for the number in the URL path.

### Method 3: CJ Email Notification

Check the email you received from CJ. It should list:
```
Advertiser: Carl Friedrik
Advertiser ID: [NUMBER HERE]
```

### Method 4: Ask CJ Support

Email: affiliatesupport@cj.com
Subject: "Need Advertiser ID for Carl Friedrik"

---

## Your CJ Account Details (For Reference)

From your feed notification:

```
CID: 7811141                    ← Your CJ account ID (Neoxus)
Company Name: Neoxus            ← Your company
Website Name: Neoxus            ← Your website
```

These are **YOUR identifiers**, not Carl Friedrik's.

**Carl Friedrik** is a separate advertiser in CJ's network with their own advertiser ID.

---

## Backend Configuration

The advertiser ID is used in the code to:

1. **Create External Keys** for products:
   ```kotlin
   externalKey = "CJ:${advertiserId}:${sha256(sku|url|title)}"
   // Example: "CJ:4567890:abc123def..."
   ```

2. **Generate Affiliate Links**:
   ```kotlin
   affiliateUrl = "https://www.anrdoezrs.net/click-${yourCID}-${advertiserId}?url=${productUrl}"
   ```

3. **Attribute Offers**:
   ```kotlin
   AffiliateMerchant(
       network = "CJ",
       programId = advertiserId,  // Carl Friedrik's ID
       name = "Carl Friedrik",
       websiteUrl = "https://carlfri edrik.com"
   )
   ```

---

## Can We Make It Automatic?

### Option 1: Extract from Feed URL ✅ Possible

If the feed URL contains the advertiser ID, we could parse it:

```kotlin
fun extractAdvertiserIdFromUrl(url: String): Long? {
    // https://datafeed.cj.com/1234567/Carl_Friedrik-...
    val regex = Regex("datafeed\\.cj\\.com/(\\d+)/")
    return regex.find(url)?.groupValues?.get(1)?.toLongOrNull()
}
```

**Status**: Not implemented yet, but we can add this

### Option 2: Database Configuration ✅ Already Supported

Once you discover feeds via SFTP, advertiser IDs are stored:

```sql
cj_feed_config:
  advertiser_id: 1234567
  feed_name: "Carl_Friedrik_Product_Feed_UK"
  feed_url: "sftp://..."
```

Then you can sync without providing the ID again.

### Option 3: Default Advertiser ❌ Not Recommended

Setting a global default advertiser ID would break multi-advertiser support.

---

## Recommended Workflow

### First Time Setup:

**Option A: If you know the advertiser ID**
```bash
curl -X POST /api/v1/admin/cj/feed/ingest \
  -d '{
    "advertiserId": 1234567,  # Carl Friedrik's ID
    "url": "https://datafeed.cj.com/.../feed.xml.zip"
  }'
```

**Option B: Use SFTP Discovery**
```bash
# Discovers all feeds and stores advertiser IDs in database
curl -X POST /api/v1/admin/cj/feed/configs/discover \
  -d '{
    "sftpHost": "datatransfer.cj.com",
    "sftpUsername": "7811141",
    "sftpPassword": "CqjH9na=",
    "advertiserId": 1234567,  # Carl Friedrik's ID
    "advertiserName": "Carl Friedrik"
  }'
```

### Subsequent Syncs:

```bash
# Advertiser ID stored in database, no need to provide again
curl -X POST /api/v1/admin/cj/feed/configs/{configId}/sync
```

---

## Example: Carl Friedrik Feed

Based on your notification:

```
Feed Name: Carl_Friedrik-Carl_Friedrik_Product_Feed_UK-shopping.xml.zip
CID: 7811141 (your account)
Advertiser: Carl Friedrik (the merchant)
Advertiser ID: ??? (you need to find this)
```

**To find Carl Friedrik's ID:**
1. Check the feed download URL in your CJ account
2. Or search for Carl Friedrik in CJ advertiser lookup
3. Or check if it's in the feed URL they email you

---

## Summary

| Term | What It Is | Example |
|------|-----------|---------|
| **CID** | Your CJ account ID | 7811141 (Neoxus) |
| **Company Name** | Your company | Neoxus |
| **Advertiser** | The merchant/brand | Carl Friedrik |
| **Advertiser ID** | Merchant's unique CJ ID | ??? (need to find) |

**Bottom Line**: You need Carl Friedrik's advertiser ID to ingest their products. Check your CJ account dashboard or the feed URL to find it.

---

## Next Steps

1. **Find Carl Friedrik's Advertiser ID** using methods above
2. **Use it in manual ingest OR feed discovery**
3. **System stores it** so you don't need to provide it again

If you can't find it, email CJ support or check the feed URL they send you!
