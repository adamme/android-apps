/**
 * YellowPicnic Upcoming Meals Sync
 * Logs into https://yellowpicnic.com/account/, reads the meals selected under "Next Delivery",
 * checks all meal names against the YellowPicnic tab in the Google Sheet, and adds any new
 * unique meal names to the top of the YellowPicnic sheet.
 */
const YELLOW_PICNIC_ACCOUNT_URL = "https://yellowpicnic.com/account/";
const BROWSER_USER_AGENT =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

function syncYellowPicnicUpcomingMeals() {
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const sheet = getSheetForTab(ss, "YellowPicnic");
    if (!sheet) {
      throw new Error("YellowPicnic sheet tab not found.");
    }

    const lastRow = sheet.getLastRow();
    const existingMealRowByNormalizedName = {};

    if (lastRow >= 1) {
      const colBValues = sheet.getRange(1, 2, lastRow, 1).getDisplayValues();
      const colBRichText = sheet.getRange(1, 2, lastRow, 1).getRichTextValues();
      for (let i = 0; i < colBValues.length; i++) {
        const name = normalizeMealName(colBValues[i][0]);
        if (name && name !== "meal" && name !== "meals") {
          const rowNum = i + 1;
          const existingLink = colBRichText[i][0] ? colBRichText[i][0].getLinkUrl() : null;
          if (!existingMealRowByNormalizedName[name]) {
            existingMealRowByNormalizedName[name] = {
              row: rowNum,
              displayText: colBValues[i][0],
              hasLink: Boolean(existingLink)
            };
          }
        }
      }
    }

    // Backfill any unrated top rows on the sheet from the public /all-meals/ catalog if they lack a link
    const catalogMap = fetchYellowPicnicAllMealsCatalogImageMap();
    for (const normKey of Object.keys(existingMealRowByNormalizedName)) {
      const entry = existingMealRowByNormalizedName[normKey];
      if (entry && entry.row > 0 && !entry.hasLink && catalogMap[normKey]) {
        const richText = SpreadsheetApp.newRichTextValue()
          .setText(entry.displayText)
          .setLinkUrl(catalogMap[normKey])
          .build();
        sheet.getRange(entry.row, 2).setRichTextValue(richText);
        entry.hasLink = true;
      }
    }

    const upcomingMeals = fetchYellowPicnicUpcomingMeals();
    Logger.log("Upcoming meals on YellowPicnic account: " + JSON.stringify(upcomingMeals));

    if (!upcomingMeals || upcomingMeals.length === 0) {
      Logger.log("No upcoming meals found on YellowPicnic account page.");
      return { synced: 0, addedMeals: [], upcomingMeals: [] };
    }

    // Deduplicate upcoming meals, backfill missing image links on existing rows, and collect new unique meals
    const newUniqueMeals = [];
    for (const item of upcomingMeals) {
      const rawName = typeof item === "string" ? item : item && item.name;
      const imageUrl = typeof item === "object" && item ? item.imageUrl || catalogMap[normalizeMealName(rawName)] || null : null;
      const cleanName = (rawName || "").replace(/\s+/g, " ").trim();
      const norm = normalizeMealName(cleanName);
      if (!norm) continue;

      const existingEntry = existingMealRowByNormalizedName[norm];
      if (existingEntry) {
        if (imageUrl && !existingEntry.hasLink) {
          const richText = SpreadsheetApp.newRichTextValue()
            .setText(existingEntry.displayText || cleanName)
            .setLinkUrl(imageUrl)
            .build();
          sheet.getRange(existingEntry.row, 2).setRichTextValue(richText);
          existingEntry.hasLink = true;
        }
        continue;
      }

      existingMealRowByNormalizedName[norm] = {
        row: -1,
        displayText: cleanName,
        hasLink: Boolean(imageUrl)
      };
      newUniqueMeals.push({ name: cleanName, imageUrl: imageUrl });
    }

    if (newUniqueMeals.length === 0) {
      Logger.log("All upcoming YellowPicnic meals are already on the sheet.");
      return { synced: 0, addedMeals: [], upcomingMeals: upcomingMeals };
    }

    const topMealRow = findFirstMealRow(sheet);
    const maxCols = Math.max(sheet.getLastColumn(), 5);

    // Insert in reverse order at topMealRow so the final top-to-bottom order matches the delivery carousel
    for (let i = newUniqueMeals.length - 1; i >= 0; i--) {
      const mealItem = newUniqueMeals[i];
      sheet.insertRowBefore(topMealRow);

      // Copy formatting and Column E (Average) formula from the existing meal row (now at topMealRow + 1)
      if (sheet.getLastRow() >= topMealRow + 1) {
        const templateRowRange = sheet.getRange(topMealRow + 1, 1, 1, maxCols);
        const targetRowRange = sheet.getRange(topMealRow, 1, 1, maxCols);
        templateRowRange.copyTo(targetRowRange, SpreadsheetApp.CopyPasteType.PASTE_FORMAT, false);

        const templateAverageCell = sheet.getRange(topMealRow + 1, 5);
        const templateFormula = templateAverageCell.getFormula();
        if (templateFormula) {
          templateAverageCell.copyTo(
            sheet.getRange(topMealRow, 5),
            SpreadsheetApp.CopyPasteType.PASTE_FORMULA,
            false
          );
          if (/^=\s*ARRAYFORMULA/i.test(templateFormula)) {
            templateAverageCell.clearContent();
          }
        }
      }

      // Set Meal Name (+ Image URL hyperlink if available) in Column B; leave Date (Col A), Anissa (Col C), Adamme (Col D) empty
      sheet.getRange(topMealRow, 1).clearContent();
      const mealCell = sheet.getRange(topMealRow, 2);
      if (mealItem.imageUrl) {
        const richText = SpreadsheetApp.newRichTextValue()
          .setText(mealItem.name)
          .setLinkUrl(mealItem.imageUrl)
          .build();
        mealCell.setRichTextValue(richText);
      } else {
        mealCell.setValue(mealItem.name);
      }
      sheet.getRange(topMealRow, 3).clearContent();
      sheet.getRange(topMealRow, 4).clearContent();
    }

    const addedNames = newUniqueMeals.map(function (m) {
      return m.name;
    });
    Logger.log(
      "Added " +
        addedNames.length +
        " new YellowPicnic meal(s) to top of sheet (row " +
        topMealRow +
        "): " +
        JSON.stringify(addedNames)
    );
    return { synced: addedNames.length, addedMeals: addedNames, upcomingMeals: upcomingMeals };
  } finally {
    lock.releaseLock();
  }
}

function fetchYellowPicnicUpcomingMeals() {
  const scriptProps = PropertiesService.getScriptProperties();
  const username = scriptProps.getProperty("YELLOW_PICNIC_USERNAME");
  const password = scriptProps.getProperty("YELLOW_PICNIC_PASSWORD");
  if (!username || !password) {
    throw new Error("Missing YELLOW_PICNIC_USERNAME or YELLOW_PICNIC_PASSWORD in Script Properties.");
  }

  const cookieJar = {};

  // 1. GET https://yellowpicnic.com/account/ to obtain initial cookies and woocommerce-login-nonce
  const getResp = UrlFetchApp.fetch(YELLOW_PICNIC_ACCOUNT_URL, {
    method: "get",
    followRedirects: true,
    muteHttpExceptions: true,
    headers: {
      "User-Agent": BROWSER_USER_AGENT,
      "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "Accept-Language": "en-US,en;q=0.9"
    }
  });

  mergeSetCookies(cookieJar, getResp.getAllHeaders());
  const loginPageHtml = getResp.getContentText();

  const nonceMatch =
    loginPageHtml.match(/name=["']woocommerce-login-nonce["'][^>]*value=["']([^"']+)["']/i) ||
    loginPageHtml.match(/id=["']woocommerce-login-nonce["'][^>]*value=["']([^"']+)["']/i) ||
    loginPageHtml.match(/value=["']([^"']+)["'][^>]*name=["']woocommerce-login-nonce["']/i);

  if (!nonceMatch || !nonceMatch[1]) {
    throw new Error("Could not find woocommerce-login-nonce on YellowPicnic login page.");
  }
  const loginNonce = nonceMatch[1];

  // 2. POST credentials with followRedirects: false so we capture the 302 Set-Cookie headers
  const postPayload = {
    "username": username,
    "password": password,
    "rememberme": "forever",
    "woocommerce-login-nonce": loginNonce,
    "_wp_http_referer": "/account/",
    "login": "Log in"
  };

  const postResp = UrlFetchApp.fetch(YELLOW_PICNIC_ACCOUNT_URL, {
    method: "post",
    payload: postPayload,
    followRedirects: false,
    muteHttpExceptions: true,
    headers: {
      "User-Agent": BROWSER_USER_AGENT,
      "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "Cookie": formatCookieHeader(cookieJar),
      "Origin": "https://yellowpicnic.com",
      "Referer": YELLOW_PICNIC_ACCOUNT_URL
    }
  });

  mergeSetCookies(cookieJar, postResp.getAllHeaders());

  // 3. Fetch the authenticated /account/ dashboard page using the logged-in cookies
  let accountHtml = "";
  const postStatus = postResp.getResponseCode();
  if (postStatus >= 300 && postStatus < 400) {
    const authGetResp = UrlFetchApp.fetch(YELLOW_PICNIC_ACCOUNT_URL, {
      method: "get",
      followRedirects: true,
      muteHttpExceptions: true,
      headers: {
        "User-Agent": BROWSER_USER_AGENT,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Cookie": formatCookieHeader(cookieJar),
        "Referer": YELLOW_PICNIC_ACCOUNT_URL
      }
    });
    accountHtml = authGetResp.getContentText();
  } else {
    accountHtml = postResp.getContentText();
  }

  return parseYellowPicnicNextDeliveryMeals(accountHtml);
}

function parseYellowPicnicNextDeliveryMeals(accountHtml) {
  if (!accountHtml) return [];

  const deliverySectionMatch = accountHtml.match(
    /<section[^>]*class=["'][^"']*ycwc-card--delivery[^"']*["'][^>]*>([\s\S]*?)<\/section>/i
  );
  const targetHtml = deliverySectionMatch ? deliverySectionMatch[1] : accountHtml;

  const meals = [];
  const cardRegex = /<li[^>]*class=["'][^"']*ycwc-meals__card[^"']*["'][^>]*>([\s\S]*?)<\/li>/gi;
  let cardMatch;
  while ((cardMatch = cardRegex.exec(targetHtml)) !== null) {
    const cardHtml = cardMatch[1];
    const nameMatch = cardHtml.match(
      /<span[^>]*class=["'][^"']*ycwc-meals__name[^"']*["'][^>]*>([\s\S]*?)<\/span>/i
    );
    if (!nameMatch) continue;

    const rawText = nameMatch[1].replace(/<[^>]+>/g, "");
    const decodedName = decodeHtmlEntities(rawText).replace(/\s+/g, " ").trim();
    if (!decodedName) continue;

    let imageUrl = null;
    const dataSrcMatch = cardHtml.match(/<img[^>]+data-src=["'](https?:\/\/[^"']+)["']/i);
    if (dataSrcMatch && dataSrcMatch[1]) {
      imageUrl = decodeHtmlEntities(dataSrcMatch[1]).trim();
    } else {
      const srcMatches = cardHtml.match(/<img[^>]+src=["'](https?:\/\/[^"']+)["']/gi);
      if (srcMatches && srcMatches.length > 0) {
        const urlMatch = srcMatches[0].match(/src=["'](https?:\/\/[^"']+)["']/i);
        if (urlMatch && urlMatch[1]) {
          imageUrl = decodeHtmlEntities(urlMatch[1]).trim();
        }
      }
    }

    meals.push({
      name: decodedName,
      imageUrl: imageUrl
    });
  }

  // Fallback if card wrapper structure changes
  if (meals.length === 0) {
    const nameRegex = /<span[^>]*class=["'][^"']*ycwc-meals__name[^"']*["'][^>]*>([\s\S]*?)<\/span>/gi;
    let match;
    while ((match = nameRegex.exec(targetHtml)) !== null) {
      const rawText = match[1].replace(/<[^>]+>/g, "");
      const decoded = decodeHtmlEntities(rawText).replace(/\s+/g, " ").trim();
      if (decoded) {
        meals.push({ name: decoded, imageUrl: null });
      }
    }
  }

  return meals;
}

function fetchYellowPicnicAllMealsCatalogImageMap() {
  const map = {};
  try {
    const resp = UrlFetchApp.fetch("https://yellowpicnic.com/all-meals/", {
      method: "get",
      followRedirects: true,
      muteHttpExceptions: true,
      headers: {
        "User-Agent": BROWSER_USER_AGENT
      }
    });
    const html = resp.getContentText();
    const imgRegex = /alt=["']([^"']+)["'][^>]+data-src=["'](https:\/\/yellowpicnic\.com\/wp-content\/uploads\/[^"']+)["']/gi;
    let m;
    while ((m = imgRegex.exec(html)) !== null) {
      const name = normalizeMealName(decodeHtmlEntities(m[1]));
      const url = decodeHtmlEntities(m[2]).trim();
      if (name && url && !map[name]) {
        map[name] = url;
      }
    }
  } catch (_) {}
  return map;
}

function mergeSetCookies(cookieJar, allHeaders) {
  if (!allHeaders) return;
  let setCookieHeader = null;
  if (typeof allHeaders === "object" && !Array.isArray(allHeaders)) {
    for (const k of Object.keys(allHeaders)) {
      if (String(k).toLowerCase() === "set-cookie") {
        setCookieHeader = allHeaders[k];
        break;
      }
    }
  } else {
    setCookieHeader = allHeaders;
  }
  if (!setCookieHeader) return;
  const rawList = Array.isArray(setCookieHeader) ? setCookieHeader : [setCookieHeader];
  for (const cookieStr of rawList) {
    const firstPair = String(cookieStr).split(";")[0].trim();
    const eqIdx = firstPair.indexOf("=");
    if (eqIdx > 0) {
      const name = firstPair.substring(0, eqIdx).trim();
      const value = firstPair.substring(eqIdx + 1).trim();
      if (value && value !== "deleted") {
        cookieJar[name] = value;
      }
    }
  }
}

function formatCookieHeader(cookieJar) {
  return Object.keys(cookieJar)
    .map(function (k) {
      return k + "=" + cookieJar[k];
    })
    .join("; ");
}

function decodeHtmlEntities(str) {
  if (!str) return "";
  return String(str)
    .replace(/&#(\d+);/g, function (_, dec) {
      return String.fromCharCode(Number(dec));
    })
    .replace(/&#x([0-9a-fA-F]+);/g, function (_, hex) {
      return String.fromCharCode(parseInt(hex, 16));
    })
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#039;|&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&nbsp;/g, " ");
}

function normalizeMealName(name) {
  return (name || "")
    .toString()
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function findFirstMealRow(sheet) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return 3;

  const scanCount = Math.min(lastRow, 15);
  const values = sheet.getRange(1, 1, scanCount, 2).getDisplayValues();
  let headerRow = 1;

  for (let i = 0; i < values.length; i++) {
    const colA = normalizeMealName(values[i][0]);
    const colB = normalizeMealName(values[i][1]);
    if (colA === "date" || colB === "meal" || colB === "meals") {
      headerRow = i + 1;
    }
  }

  for (let r = headerRow + 1; r <= scanCount; r++) {
    const colB = normalizeMealName(values[r - 1][1]);
    if (colB && colB !== "meal" && colB !== "meals") {
      return r;
    }
  }

  return Math.max(headerRow + 1, 3);
}
