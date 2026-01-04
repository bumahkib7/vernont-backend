# CJ Feed Management Admin UI

## Overview

The CJ Feed Management interface provides a comprehensive dashboard for managing Commission Junction product feeds with **both automation and manual control**.

**Location**: `/networks/cj-feeds` in vernont-admin-next

**Framework**: Next.js 16 with React 19, TypeScript, Tailwind CSS

---

## ✅ Removed Duplicate UI

**Before**: There were 2 duplicate pages:
- `/networks/cj-feed` (singular) - Manual ingestion only
- `/networks/cj-feeds` (plural) - Feed configuration only

**After**: Unified single page at `/networks/cj-feeds` with ALL features

---

## 🎯 Key Features

### 1. **Automation Features**

✅ **Feed Configuration Management**
- Enable/disable individual feeds
- Configure feed priorities
- Track last sync status and timestamps
- Filter feeds by status (all/enabled/disabled)

✅ **SFTP Feed Discovery**
- Scan CJ SFTP server for available feeds
- Automatically create feed configurations
- Enable all discovered feeds by default

✅ **Batch Operations**
- Sync all enabled feeds with one click
- View statistics across all feeds
- Bulk management capabilities

✅ **Auto-Refresh**
- Real-time run monitoring (every 5 seconds)
- Progress updates without page reload
- Live status badges with animations

### 2. **Manual Control Features**

✅ **Manual Feed Ingest**
- Trigger feed ingestion for any advertiser ID
- Provide custom feed URLs
- Override configured feed settings

✅ **Individual Feed Control**
- Enable/disable specific feeds
- Sync individual feeds on-demand
- Monitor per-feed sync status

✅ **Run Management**
- Cancel running feed ingestions
- View detailed progress (items processed, percentage complete)
- Monitor multiple concurrent runs

---

## 📊 Dashboard Components

### Stats Cards (7 Total)

| Card | Description |
|------|-------------|
| **Feeds** | Total number of feed configurations |
| **Enabled** | Number of enabled feeds |
| **Disabled** | Number of disabled feeds |
| **Runs** | Total feed ingestion runs |
| **Running** | Currently active runs |
| **Completed** | Successfully completed runs |
| **Failed** | Failed runs requiring attention |

### Manual Ingest Form

**Fields**:
- **Advertiser ID** (required) - CJ advertiser ID (e.g., 7811141)
- **Feed URL** (optional) - Override feed URL

**Behavior**:
- Toggles on/off with "Manual Ingest" button
- Validates advertiser ID before submitting
- Clears form after successful start
- Shows toast notifications for success/failure

### Recent Feed Runs

**Features**:
- Displays last 10 runs
- Auto-refreshes every 5 seconds
- Progress bars with percentage complete
- Color-coded status badges
- Cancel button for active runs

**Status Badges**:
- 🟢 **Completed** - Green with checkmark
- 🔵 **Running** - Blue with spinning icon (animated)
- 🔴 **Failed** - Red with error icon
- 🟡 **Started** - Yellow with clock icon
- ⚫ **Cancelled** - Gray with X icon

### Feed Configuration List

**Display**:
- Feed name with truncation for long names
- Region badge (UK, EU, US, DE, NL)
- Language badge (English, German, Dutch, French)
- Enabled/disabled status icon
- Advertiser ID and priority
- Last sync timestamp (e.g., "5m ago", "2h ago")
- Last sync status badge

**Actions Per Feed**:
- **Enable/Disable** toggle button
- **Sync** button (only for enabled feeds)

### SFTP Discovery Dialog

**Configuration**:
- SFTP Host (default: datatransfer.cj.com)
- Port (default: 22)
- Username (pre-filled: 7811141)
- Password (secure input)
- Advertiser ID (pre-filled: 7811141)
- Advertiser Name (optional)

**Behavior**:
- Scans SFTP for .zip feed files
- Creates feed configs in database
- Shows discovery results (discovered, enabled)
- Closes automatically on success

---

## 🎨 UI/UX Details

### Responsive Design
- Mobile-first approach
- Adapts from 1 column (mobile) to 7 columns (desktop) for stats
- Collapsible manual ingest form
- Scrollable feed lists

### Dark Mode Support
- All components support dark mode
- Color-coded badges adjust for dark theme
- Border and background colors adapt automatically

### Animations
- Running status badge pulses
- Loading spinners on refresh/sync buttons
- Progress bar smooth transitions
- Hover effects on cards and buttons

### Toast Notifications
All actions provide feedback:
- ✅ Success: Green toast with checkmark
- ❌ Error: Red toast with error message
- ⚠️ Warning: Yellow toast for validation issues

---

## 🔌 API Integration

### Feed Configuration API (`cjFeedConfigApi`)

```typescript
// List all feed configurations
listAllFeeds(): Promise<CjFeedConfig[]>

// Toggle feed enabled status
toggleFeed(id: number, enabled: boolean): Promise<void>

// Sync individual feed
syncFeed(id: number): Promise<{ runId: string }>

// Sync all enabled feeds
syncAllEnabled(): Promise<{ totalRuns: number, runIds: string[] }>

// Discover feeds from SFTP
discoverFeeds(request: DiscoverFeedsRequest): Promise<{
  discovered: number
  enabled: number
  skipped: number
}>
```

### Feed Ingest API (`cjFeedApi`)

```typescript
// List all feed runs
listRuns(advertiserId?: number): Promise<CjFeedRun[]>

// Start manual ingest
startIngest(request: {
  advertiserId: number
  url?: string
  format?: string
  dryRun?: boolean
}): Promise<{ status: string, runId: string }>

// Cancel running feed
cancel(runId: string): Promise<void>

// Get run details
getRun(runId: string): Promise<CjFeedRun>
```

---

## 🚀 Usage Examples

### Scenario 1: First-Time Setup

1. **Discover Feeds**:
   - Click "Discover Feeds"
   - Enter SFTP credentials (username: 7811141)
   - Click "Discover Feeds"
   - Wait for scan to complete
   - View discovered feeds in the list

2. **Review and Configure**:
   - Check which feeds were enabled
   - Disable unwanted feeds
   - Adjust priorities if needed

3. **Initial Sync**:
   - Click "Sync All Enabled"
   - Monitor progress in "Recent Feed Runs"
   - Wait for completion

### Scenario 2: Manual Feed Ingestion

1. **Open Manual Ingest Form**:
   - Click "Manual Ingest" button

2. **Enter Feed Details**:
   - Advertiser ID: `7811141` (for Carl Friedrik)
   - Feed URL: `https://datafeed.cj.com/.../Carl_Friedrik-Carl_Friedrik_Product_Feed_UK-shopping.xml.zip`

3. **Start Ingestion**:
   - Click "Start Ingest"
   - Monitor in "Recent Feed Runs"
   - See real-time progress updates

### Scenario 3: Monitoring Active Runs

1. **View Active Runs**:
   - Scroll to "Recent Feed Runs"
   - See all runs with status badges

2. **Monitor Progress**:
   - Watch progress bar update in real-time
   - See item count: "5,000 / 15,234 items (33%)"

3. **Cancel if Needed**:
   - Click "Cancel" on active run
   - Confirm cancellation
   - Run status changes to "Cancelled"

### Scenario 4: Routine Maintenance

1. **Check Stats**:
   - View dashboard to see overall health
   - Note any failed runs

2. **Investigate Failures**:
   - Filter to "Failed" runs
   - Click on individual feeds to see details
   - Check logs for error messages

3. **Re-sync Failed Feeds**:
   - Click "Sync" on specific feed
   - Or use "Sync All Enabled" to retry all

---

## 🎯 Best Practices

### Automation Strategy

✅ **DO**:
- Use SFTP discovery for initial setup
- Enable feeds you want to auto-sync
- Use "Sync All Enabled" for routine updates
- Set up scheduled syncs (backend cron job)

❌ **DON'T**:
- Enable all discovered feeds blindly
- Run sync-all during peak traffic hours
- Forget to monitor failed syncs

### Manual Control Strategy

✅ **DO**:
- Use manual ingest for urgent updates
- Provide feed URL for one-time imports
- Monitor progress for large feeds
- Cancel stuck runs to free resources

❌ **DON'T**:
- Manually trigger feeds that are auto-synced
- Start multiple concurrent runs for same advertiser
- Ignore failure notifications

### Performance Tips

1. **Filter Views**: Use enabled/disabled filters to reduce visual clutter
2. **Cancel Unused Runs**: Don't let failed runs pile up
3. **Monitor Stats**: Watch for abnormal run counts
4. **Auto-Refresh**: Relies on active runs - no overhead when idle

---

## 🔧 Troubleshooting

### Issue: "No feeds found"

**Solution**: Click "Discover Feeds" to scan SFTP and create configurations

### Issue: Feed stuck in "Running" status

**Solution**:
1. Check backend logs: `tail -f vernont-api/logs/application.log | grep "CJ"`
2. Cancel the run via UI
3. Retry sync

### Issue: Discovery fails with "Connection refused"

**Solution**:
1. Verify SFTP credentials in dialog
2. Check firewall/network access to `datatransfer.cj.com:22`
3. Confirm username/password are correct

### Issue: Progress bar not updating

**Solution**:
1. Check browser console for errors
2. Verify backend is running
3. Refresh page to re-establish connection
4. Auto-refresh interval is 5 seconds

---

## 📱 Mobile Experience

The UI is fully responsive:
- **Mobile**: Single column layout, collapsible sections
- **Tablet**: 2-4 columns for stats, stacked forms
- **Desktop**: Full 7-column stats, side-by-side layouts

Touch-optimized:
- Larger tap targets for buttons
- Swipeable feed lists
- Mobile-friendly dialogs

---

## 🎨 Customization

### Color Scheme

All colors use Tailwind CSS theme variables:
- `--foreground` - Primary text
- `--muted-foreground` - Secondary text
- `--card` - Card backgrounds
- `--border` - Border colors

### Status Colors

```css
Completed: green-600 / green-400 (dark)
Running:   blue-600 / blue-400 (dark)
Failed:    red-600 / red-400 (dark)
Started:   yellow-600 / yellow-400 (dark)
Cancelled: gray-600 / gray-400 (dark)
```

---

## 🔐 Security

### Access Control
- All endpoints require ADMIN role
- JWT authentication enforced
- SFTP credentials encrypted in transit

### Data Protection
- SFTP password masked in UI
- Feed URLs not exposed in frontend
- Sensitive data redacted in logs

---

## 📈 Future Enhancements

Potential improvements:
- [ ] WebSocket for real-time updates (replace polling)
- [ ] Feed scheduling UI (cron expression builder)
- [ ] Run history charts and analytics
- [ ] Export run logs to CSV
- [ ] Feed performance metrics (avg time, success rate)
- [ ] Email notifications for failed runs
- [ ] Webhook configuration for completed feeds

---

## 🎯 Summary

The unified CJ Feed Management UI provides:

✅ **Automation**: SFTP discovery, batch syncing, auto-refresh
✅ **Manual Control**: Custom triggers, individual management, cancellation
✅ **Monitoring**: Real-time progress, stats dashboard, run history
✅ **User-Friendly**: Responsive, dark mode, toast notifications
✅ **Production-Ready**: Error handling, validation, security

**Result**: Single powerful interface replacing 2 duplicate UIs with enhanced features and better UX.
