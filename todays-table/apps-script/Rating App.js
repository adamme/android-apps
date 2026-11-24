/**
 * Google Apps Script Backend for Today's Table (HelloFresh + YellowPicnic)
 * Spreadsheet ID: YOUR_SPREADSHEET_ID
 */
const TABS = ["HelloFresh", "YellowPicnic"];

function getSheetForTab(ss, tabName) {
  let sheet = ss.getSheetByName(tabName);
  if (sheet) return sheet;
  // Fallback case-insensitive match (or gid=0 for HelloFresh if renamed)
  const lower = tabName.toLowerCase().replace(/\s+/g, "");
  for (const s of ss.getSheets()) {
    if (s.getName().toLowerCase().replace(/\s+/g, "") === lower) return s;
  }
  if (tabName === "HelloFresh") {
    for (const s of ss.getSheets()) {
      if (s.getSheetId() === 0) return s;
    }
  }
  return null;
}

/**
 * Returns true if the row has already been rated or completed.
 * Any meal that has ANYTHING listed in Column E (Average, index 4) — whether a number
 * (e.g. 8, 9.5) or a "-" hyphen — is treated as rated and complete and must NOT show in the app.
 */
function isRowRatedOrComplete(row) {
  const dateStr = (row[0] || "").toString().trim();
  const anissa = (row[2] || "").toString().trim();
  const adamme = (row[3] || "").toString().trim();
  const average = (row[4] || "").toString().trim();

  // Column E (Average): any number or "-" hyphen means rated and complete
  if (average !== "") {
    return true;
  }

  // Also treat as rated if Anissa or Adamme already has a score or "-"
  if (anissa !== "" || adamme !== "") {
    return true;
  }

  if (dateStr !== "" && anissa !== "" && adamme !== "") {
    return true;
  }

  return false;
}

// GET: Returns a single unified list of unrated meals across HelloFresh & YellowPicnic
function doGet(e) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const allUnratedMeals = [];

  for (const tabName of TABS) {
    const sheet = getSheetForTab(ss, tabName);
    if (!sheet) continue;

    const lastRow = sheet.getLastRow();
    if (lastRow < 2) continue;

    const maxRows = lastRow - 1;
    // Read Columns A through E (5 columns: Date, Meal, Anissa, Adamme, Average)
    const values = sheet.getRange(2, 1, maxRows, 5).getDisplayValues();
    const richText = sheet.getRange(2, 2, maxRows, 1).getRichTextValues();

    for (let i = 0; i < values.length; i++) {
      const row = values[i];
      const mealName = (row[1] || "").toString().trim();
      if (!mealName) continue;

      // Skip any meal that has a number or "-" in Column E (Average)
      if (isRowRatedOrComplete(row)) {
        continue;
      }

      const linkUrl = richText[i][0] ? richText[i][0].getLinkUrl() : null;
      const isDirectImageLink =
        Boolean(linkUrl) &&
        (/\.(?:jpe?g|png|webp|gif|avif)(?:[?#]|$)/i.test(linkUrl) ||
          /\/wp-content\/uploads\//i.test(linkUrl));
      const imageUrl = isDirectImageLink
        ? linkUrl
        : resolveMealImage(tabName, mealName, linkUrl);

      allUnratedMeals.push({
        tab: tabName,
        rowNumber: i + 2,
        meal: mealName,
        recipeUrl: isDirectImageLink ? null : (linkUrl || null),
        imageUrl: imageUrl,
        average: ""
      });
    }
  }

  return jsonResponse({ meals: allUnratedMeals });
}

// POST: Writes Date (Col A), Anissa (Col C), Adamme (Col D), optional Notes, & moves row below unrated
// Does NOT write to Column E (Average) — Average is calculated by the formula in the sheet.
function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const body = JSON.parse(e.postData.contents);
    const { tab, rowNumber, meal, date, anissa, adamme, notes } = body;

    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const sheet = getSheetForTab(ss, tab || "HelloFresh");
    if (!sheet) {
      return jsonResponse({ error: "Tab not found: " + tab });
    }

    const targetRow = findMealRow(sheet, rowNumber, meal);
    if (!targetRow) {
      return jsonResponse({ error: "Meal row not found in " + tab });
    }

    // 1. Write Date (Col A), Anissa (Col C), Adamme (Col D) preserving Col B hyperlink & Col E formula
    sheet.getRange(targetRow, 1).setValue(date);

    const anissaStr = (anissa !== undefined && anissa !== null) ? String(anissa).trim() : "";
    const adammeStr = (adamme !== undefined && adamme !== null) ? String(adamme).trim() : "";
    const anissaNum = Number(anissaStr);
    const adammeNum = Number(adammeStr);
    const hasAnissaNum = anissaStr !== "" && anissaStr !== "-" && !isNaN(anissaNum);
    const hasAdammeNum = adammeStr !== "" && adammeStr !== "-" && !isNaN(adammeNum);

    sheet.getRange(targetRow, 3).setValue(hasAnissaNum ? anissaNum : "-");
    sheet.getRange(targetRow, 4).setValue(hasAdammeNum ? adammeNum : "-");

    const notesStr = (notes !== undefined && notes !== null) ? String(notes).trim() : "";
    if (notesStr !== "") {
      const notesCol = findNotesColumn(sheet);
      sheet.getRange(targetRow, notesCol).setValue(notesStr);
    }

    // 2. Move newly rated row right below the remaining unrated rows in this tab
    const lastUnratedRow = findLastUnratedRow(sheet, targetRow);
    if (lastUnratedRow > targetRow) {
      const rowRange = sheet.getRange(targetRow, 1, 1, sheet.getLastColumn());
      sheet.insertRowAfter(lastUnratedRow);
      rowRange.copyTo(sheet.getRange(lastUnratedRow + 1, 1));
      sheet.deleteRow(targetRow);
    }

    return jsonResponse({ success: true });
  } catch (err) {
    return jsonResponse({ error: err.message });
  } finally {
    lock.releaseLock();
  }
}

function findNotesColumn(sheet) {
  const scanRows = Math.min(Math.max(sheet.getLastRow(), 1), 5);
  const scanCols = Math.max(sheet.getLastColumn(), 6);
  const values = sheet.getRange(1, 1, scanRows, scanCols).getDisplayValues();
  for (let r = 0; r < values.length; r++) {
    for (let c = 0; c < values[r].length; c++) {
      const header = (values[r][c] || "").toString().trim().toLowerCase();
      if (header === "notes" || header === "note") {
        return c + 1;
      }
    }
  }
  return 6;
}

function resolveMealImage(tabName, mealName, recipeUrl) {
  if (tabName === "YellowPicnic") {
    return null;
  }

  const cache = CacheService.getScriptCache();
  const cacheKey = "img_" + tabName + "_" + Utilities.base64Encode(mealName).slice(0, 50);
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  let imageUrl = null;
  // 1. If Column B has a hyperlink, try extracting og:image first
  if (recipeUrl) {
    try {
      const html = UrlFetchApp.fetch(recipeUrl, { muteHttpExceptions: true }).getContentText();
      const ogMatch = html.match(/<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']/i)
                   || html.match(/<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']/i);
      if (ogMatch && ogMatch[1]) imageUrl = ogMatch[1];
    } catch (_) {}
  }

  // 2. Image search: prefix with "HelloFresh" for HelloFresh tab
  if (!imageUrl) {
    try {
      const searchQuery = "HelloFresh " + mealName;
      const searchUrl = "https://www.bing.com/images/search?q=" + encodeURIComponent(searchQuery) + "&first=1";
      const html = UrlFetchApp.fetch(searchUrl, {
        muteHttpExceptions: true,
        headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" }
      }).getContentText();
      const murlMatch = html.match(/murl&quot;:&quot;(https?:\/\/[^&]+?)&quot;/i);
      if (murlMatch && murlMatch[1]) {
        imageUrl = murlMatch[1];
      }
    } catch (_) {}
  }

  if (imageUrl) {
    cache.put(cacheKey, imageUrl, 21600); // Cache 6 hours
  }
  return imageUrl;
}

function findMealRow(sheet, expectedRow, mealName) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return null;
  if (expectedRow >= 2 && expectedRow <= lastRow) {
    const currentMeal = sheet.getRange(expectedRow, 2).getDisplayValue().trim();
    if (currentMeal === mealName.trim()) return expectedRow;
  }
  const maxRows = lastRow - 1;
  const values = sheet.getRange(2, 1, maxRows, 5).getDisplayValues();
  // Prefer matching an unrated row first
  for (let i = 0; i < values.length; i++) {
    if (values[i][1].trim() === mealName.trim() && !isRowRatedOrComplete(values[i])) {
      return i + 2;
    }
  }
  for (let i = 0; i < values.length; i++) {
    if (values[i][1].trim() === mealName.trim()) return i + 2;
  }
  return null;
}

function findLastUnratedRow(sheet, justRatedRow) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return 1;
  const maxRows = lastRow - 1;
  const values = sheet.getRange(2, 1, maxRows, 5).getDisplayValues();
  let lastUnrated = 1;
  for (let i = 0; i < values.length; i++) {
    const rowNum = i + 2;
    if (rowNum === justRatedRow) continue;
    const row = values[i];
    const mealName = (row[1] || "").toString().trim();
    if (!mealName) continue;
    if (!isRowRatedOrComplete(row)) {
      lastUnrated = rowNum;
    } else {
      break;
    }
  }
  return lastUnrated;
}

function jsonResponse(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
