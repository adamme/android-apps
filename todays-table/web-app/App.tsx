import React, { useEffect, useState } from "react";

const DEFAULT_WEB_APP_URL =
  "https://script.google.com/macros/s/YOUR_DEPLOYMENT_ID/exec";

interface UnratedMeal {
  tab: string;
  rowNumber: number;
  meal: string;
  recipeUrl?: string | null;
  imageUrl?: string | null;
}

function splitTitle(rawMeal: string): { mainTitle: string; subtitle: string } {
  let cleaned = (rawMeal || "").trim();
  const idx = cleaned.toLowerCase().indexOf(" with ");
  if (idx > 0) {
    const withPart = cleaned.substring(idx + 1).trim();
    const half = Math.floor(withPart.length / 2);
    if (half > 6) {
      const firstHalf = withPart.substring(0, half);
      const secondHalf = withPart.substring(half);
      if (firstHalf.toLowerCase() === secondHalf.toLowerCase()) {
        cleaned = cleaned.substring(0, idx + 1) + firstHalf;
      }
    }
    return {
      mainTitle: cleaned.substring(0, idx).trim(),
      subtitle: cleaned.substring(idx + 1).trim(),
    };
  }
  return { mainTitle: cleaned, subtitle: "" };
}

function resolveMealImage(mealObj: UnratedMeal): string {
  const isYP = (mealObj.tab || "").toLowerCase().includes("yellow");
  if (
    !isYP &&
    mealObj.imageUrl &&
    !mealObj.imageUrl.toLowerCase().includes("hellofresh-logo")
  ) {
    return mealObj.imageUrl;
  }
  const { mainTitle } = splitTitle(mealObj.meal);
  const query = isYP
    ? `${mainTitle} cooked dish food photography`
    : `HelloFresh ${mainTitle}`;
  return `https://tse2.mm.bing.net/th?q=${encodeURIComponent(
    query
  )}&w=800&h=500&c=7&rs=1&p=0`;
}

function formatSheetDate(dateObj: Date): string {
  const mm = String(dateObj.getMonth() + 1).padStart(2, "0");
  const dd = String(dateObj.getDate()).padStart(2, "0");
  const yyyy = dateObj.getFullYear();
  return `${mm}/${dd}/${yyyy}`;
}

function toIsoInputDate(dateObj: Date): string {
  const mm = String(dateObj.getMonth() + 1).padStart(2, "0");
  const dd = String(dateObj.getDate()).padStart(2, "0");
  const yyyy = dateObj.getFullYear();
  return `${yyyy}-${mm}-${dd}`;
}

export default function App() {
  const [meals, setMeals] = useState<UnratedMeal[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeMeal, setActiveMeal] = useState<UnratedMeal | null>(null);
  const [datePreset, setDatePreset] = useState<"today" | "yesterday" | "custom">("today");
  const [isoDate, setIsoDate] = useState<string>(() => toIsoInputDate(new Date()));
  const [sheetDate, setSheetDate] = useState<string>(() => formatSheetDate(new Date()));
  const [anissaScore, setAnissaScore] = useState<number | null>(null);
  const [adammeScore, setAdammeScore] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(null), 3500);
  };

  const fetchMeals = async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await fetch(DEFAULT_WEB_APP_URL, { method: "GET", redirect: "follow" });
      const text = await resp.text();
      const data = JSON.parse(text);
      if (data.error) throw new Error(data.error);
      setMeals(data.meals || []);
    } catch (e: any) {
      setError(e?.message || "Failed to load unrated meals.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMeals();
  }, []);

  const openMealModal = (meal: UnratedMeal) => {
    setActiveMeal(meal);
    setAnissaScore(null);
    setAdammeScore(null);
    const now = new Date();
    setDatePreset("today");
    setIsoDate(toIsoInputDate(now));
    setSheetDate(formatSheetDate(now));
  };

  const applyPreset = (preset: "today" | "yesterday") => {
    const d = new Date();
    if (preset === "yesterday") d.setDate(d.getDate() - 1);
    setDatePreset(preset);
    setIsoDate(toIsoInputDate(d));
    setSheetDate(formatSheetDate(d));
  };

  const handleCustomDate = (val: string) => {
    if (!val) return;
    const [yyyy, mm, dd] = val.split("-");
    setDatePreset("custom");
    setIsoDate(val);
    setSheetDate(`${mm}/${dd}/${yyyy}`);
  };

  const handleSaveRating = async () => {
    if (!activeMeal || anissaScore === null || adammeScore === null) return;
    setSubmitting(true);
    const mealToSave = activeMeal;
    try {
      const response = await fetch(DEFAULT_WEB_APP_URL, {
        method: "POST",
        headers: { "Content-Type": "text/plain;charset=utf-8" },
        body: JSON.stringify({
          tab: mealToSave.tab || "HelloFresh",
          rowNumber: mealToSave.rowNumber,
          meal: mealToSave.meal,
          date: sheetDate,
          anissa: anissaScore,
          adamme: adammeScore,
        }),
        redirect: "follow",
      });
      const result = await response.json();
      if (!result.success) throw new Error(result.error || "Failed to save rating");

      const avg = (anissaScore + adammeScore) / 2;
      setActiveMeal(null);
      showToast(`Saved ${splitTitle(mealToSave.meal).mainTitle} (★ ${avg} avg)`);
      await fetchMeals();
    } catch (e: any) {
      showToast(`Error: ${e?.message || "Failed to save"}`);
    } finally {
      setSubmitting(false);
    }
  };

  const hfCount = meals.filter((m) => !(m.tab || "").toLowerCase().includes("yellow")).length;
  const ypCount = meals.length - hfCount;

  return (
    <div className="min-h-screen bg-[#FAF7F2] text-[#1C2520] pb-12 select-none">
      {/* Header */}
      <header className="sticky top-0 z-20 bg-[#FAF7F2]/95 backdrop-blur-md border-b border-stone-200/70 px-4 py-3.5 flex items-center justify-between max-w-lg mx-auto">
        <div>
          <h1 className="text-xl font-extrabold tracking-tight">Meals to Rate</h1>
          <p className="text-xs font-medium text-stone-500">
            Anissa &amp; Adamme • HelloFresh + YellowPicnic
          </p>
        </div>
        <button
          onClick={fetchMeals}
          className="px-3.5 py-2 rounded-xl bg-[#E8F5E9] text-[#1E5631] text-xs font-bold active:scale-95 transition"
        >
          ↻ Refresh
        </button>
      </header>

      <main className="max-w-lg mx-auto px-4 pt-3">
        {!loading && !error && meals.length > 0 && (
          <div className="mb-4 bg-[#E8F5E9] rounded-2xl px-4 py-3 flex items-center gap-3">
            <div className="w-9 h-9 rounded-full bg-[#1E5631] text-white font-bold text-sm flex items-center justify-center shrink-0">
              {meals.length}
            </div>
            <div>
              <p className="text-sm font-bold text-[#1E5631]">
                {meals.length === 1
                  ? "1 unrated meal waiting"
                  : `${meals.length} unrated meals waiting`}
              </p>
              <p className="text-xs text-[#1E5631]/80">
                Tap any dish to score ({hfCount} HelloFresh • {ypCount} YellowPicnic)
              </p>
            </div>
          </div>
        )}

        {loading && (
          <div className="py-24 flex flex-col items-center justify-center text-center">
            <div className="w-10 h-10 border-4 border-[#1E5631] border-t-transparent rounded-full animate-spin mb-4" />
            <p className="text-stone-600 font-medium text-sm">
              Loading unrated meals &amp; dish photos…
            </p>
          </div>
        )}

        {!loading && error && (
          <div className="py-16 px-4 flex flex-col items-center text-center">
            <h2 className="text-lg font-extrabold text-stone-800 mb-2">
              Couldn't Connect to Sheet
            </h2>
            <p className="text-sm text-stone-600 bg-stone-200/70 rounded-2xl p-4 mb-5 max-w-md">
              {error}
            </p>
            <button
              onClick={fetchMeals}
              className="px-5 py-2.5 rounded-xl bg-[#1E5631] text-white text-sm font-bold"
            >
              Retry
            </button>
          </div>
        )}

        {!loading && !error && meals.length === 0 && (
          <div className="py-20 flex flex-col items-center text-center">
            <h2 className="text-xl font-extrabold mb-1">All Meals Rated!</h2>
            <p className="text-sm text-stone-500 max-w-xs mb-6">
              Both HelloFresh and YellowPicnic tabs are completely caught up.
            </p>
            <button
              onClick={fetchMeals}
              className="px-5 py-2.5 rounded-xl bg-[#1E5631] text-white text-sm font-bold"
            >
              Check Again
            </button>
          </div>
        )}

        <div className="space-y-4">
          {meals.map((meal) => {
            const isYP = (meal.tab || "").toLowerCase().includes("yellow");
            const { mainTitle, subtitle } = splitTitle(meal.meal);
            const imgUrl = resolveMealImage(meal);
            return (
              <div
                key={`${meal.tab}_${meal.rowNumber}_${meal.meal}`}
                onClick={() => openMealModal(meal)}
                className="bg-white rounded-[22px] shadow-sm border border-stone-200/80 overflow-hidden active:scale-[0.99] transition cursor-pointer"
              >
                <div className="relative h-44 bg-stone-100">
                  <img
                    src={imgUrl}
                    alt={mainTitle}
                    className="w-full h-full object-cover"
                    loading="lazy"
                  />
                  <div className="absolute inset-0 bg-gradient-to-b from-black/25 via-transparent to-black/45" />
                  <div className="absolute top-3 inset-x-3 flex items-center justify-between">
                    <span
                      className={`px-3 py-1 rounded-full text-xs font-bold shadow-sm ${
                        isYP
                          ? "bg-[#FFF4D6] text-[#B06000]"
                          : "bg-[#E6F4EA] text-[#137333]"
                      }`}
                    >
                      {isYP ? "🧺 YellowPicnic" : "🌿 HelloFresh"}
                    </span>
                    {meal.recipeUrl && (
                      <a
                        href={meal.recipeUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        onClick={(e) => e.stopPropagation()}
                        className="px-3 py-1 rounded-full bg-black/60 text-white text-xs font-semibold"
                      >
                        Recipe ↗
                      </a>
                    )}
                  </div>
                </div>
                <div className="p-4">
                  <h2 className="text-base font-extrabold leading-snug text-stone-900">
                    {mainTitle}
                  </h2>
                  {subtitle && (
                    <p className="text-xs text-stone-500 mt-1 line-clamp-2">{subtitle}</p>
                  )}
                  <div className="border-t border-stone-100 mt-3.5 pt-3 flex items-center justify-between">
                    <div className="flex gap-2">
                      <span className="px-2.5 py-1 rounded-full bg-stone-100 text-stone-600 text-xs font-medium">
                        Anissa: —
                      </span>
                      <span className="px-2.5 py-1 rounded-full bg-stone-100 text-stone-600 text-xs font-medium">
                        Adamme: —
                      </span>
                    </div>
                    <span className="text-xs font-extrabold text-[#1E5631]">
                      Rate Meal →
                    </span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </main>

      {/* Rating Bottom Sheet */}
      {activeMeal && (
        <div className="fixed inset-0 z-40">
          <div
            onClick={() => !submitting && setActiveMeal(null)}
            className="absolute inset-0 bg-black/50 backdrop-blur-xs"
          />
          <div className="absolute bottom-0 inset-x-0 max-w-lg mx-auto bg-white rounded-t-[28px] max-h-[92vh] overflow-y-auto shadow-2xl pb-6">
            <div className="w-10 h-1.5 bg-stone-300 rounded-full mx-auto my-3" />
            <div className="px-5">
              {(() => {
                const isYP = (activeMeal.tab || "").toLowerCase().includes("yellow");
                const { mainTitle, subtitle } = splitTitle(activeMeal.meal);
                const imgUrl = resolveMealImage(activeMeal);
                return (
                  <div className="relative h-48 rounded-2xl overflow-hidden bg-stone-100 mb-4">
                    <img src={imgUrl} alt={mainTitle} className="w-full h-full object-cover" />
                    <div className="absolute inset-0 bg-gradient-to-b from-black/30 via-transparent to-black/75" />
                    <div className="absolute top-3 inset-x-3 flex items-center justify-between">
                      <span
                        className={`px-3 py-1 rounded-full text-xs font-bold shadow ${
                          isYP
                            ? "bg-[#FFF4D6] text-[#B06000]"
                            : "bg-[#E6F4EA] text-[#137333]"
                        }`}
                      >
                        {isYP ? "🧺 YellowPicnic" : "🌿 HelloFresh"}
                      </span>
                      {activeMeal.recipeUrl && (
                        <a
                          href={activeMeal.recipeUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="px-3 py-1 rounded-full bg-black/60 text-white text-xs font-semibold"
                        >
                          View Recipe ↗
                        </a>
                      )}
                    </div>
                    <div className="absolute bottom-3 inset-x-4 text-white">
                      <h3 className="text-lg font-extrabold leading-snug">{mainTitle}</h3>
                      {subtitle && (
                        <p className="text-xs text-white/90 line-clamp-2 mt-0.5">
                          {subtitle}
                        </p>
                      )}
                    </div>
                  </div>
                );
              })()}

              {/* Date Selector */}
              <div className="mb-4">
                <label className="block text-xs font-bold uppercase tracking-wider text-stone-500 mb-2">
                  Date Cooked (Column A)
                </label>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => applyPreset("today")}
                    className={`px-3.5 py-1.5 rounded-xl text-xs font-bold border ${
                      datePreset === "today"
                        ? "border-[#1E5631] bg-[#E8F5E9] text-[#1E5631]"
                        : "border-stone-300 text-stone-600"
                    }`}
                  >
                    {datePreset === "today" ? `Today (${sheetDate})` : "Today"}
                  </button>
                  <button
                    type="button"
                    onClick={() => applyPreset("yesterday")}
                    className={`px-3.5 py-1.5 rounded-xl text-xs font-bold border ${
                      datePreset === "yesterday"
                        ? "border-[#1E5631] bg-[#E8F5E9] text-[#1E5631]"
                        : "border-stone-300 text-stone-600"
                    }`}
                  >
                    {datePreset === "yesterday" ? `Yesterday (${sheetDate})` : "Yesterday"}
                  </button>
                  <input
                    type="date"
                    value={isoDate}
                    onChange={(e) => handleCustomDate(e.target.value)}
                    className="px-3 py-1.5 rounded-xl text-xs font-bold border border-stone-300 text-stone-700 bg-stone-50"
                  />
                </div>
              </div>

              {/* Anissa Rating */}
              <div className="mb-4">
                <div className="flex items-center justify-between mb-2">
                  <div>
                    <span className="text-sm font-extrabold">Anissa's Rating</span>
                    <span className="text-xs text-stone-500 ml-1">(Column C)</span>
                  </div>
                  <span className="text-xs font-bold text-[#1E5631]">
                    {anissaScore ? `${anissaScore} / 10` : "Tap 1–10"}
                  </span>
                </div>
                <div className="grid grid-cols-5 gap-2">
                  {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((num) => (
                    <button
                      key={num}
                      type="button"
                      onClick={() => setAnissaScore(num)}
                      className={`h-11 rounded-xl font-bold text-sm ${
                        anissaScore === num
                          ? "bg-[#1E5631] text-white shadow-sm"
                          : "bg-stone-100 text-stone-800 hover:bg-stone-200"
                      }`}
                    >
                      {num}
                    </button>
                  ))}
                </div>
              </div>

              {/* Adamme Rating */}
              <div className="mb-4">
                <div className="flex items-center justify-between mb-2">
                  <div>
                    <span className="text-sm font-extrabold">Adamme's Rating</span>
                    <span className="text-xs text-stone-500 ml-1">(Column D)</span>
                  </div>
                  <span className="text-xs font-bold text-[#1E5631]">
                    {adammeScore ? `${adammeScore} / 10` : "Tap 1–10"}
                  </span>
                </div>
                <div className="grid grid-cols-5 gap-2">
                  {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((num) => (
                    <button
                      key={num}
                      type="button"
                      onClick={() => setAdammeScore(num)}
                      className={`h-11 rounded-xl font-bold text-sm ${
                        adammeScore === num
                          ? "bg-[#1E5631] text-white shadow-sm"
                          : "bg-stone-100 text-stone-800 hover:bg-stone-200"
                      }`}
                    >
                      {num}
                    </button>
                  ))}
                </div>
              </div>

              {anissaScore !== null && adammeScore !== null && (
                <div className="mb-4 bg-[#E8F5E9] text-[#1E5631] rounded-xl py-2.5 px-4 text-center text-sm font-extrabold">
                  ★ Combined Meal Score: {(anissaScore + adammeScore) / 2} / 10
                </div>
              )}

              <div className="flex gap-3 pt-1">
                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => setActiveMeal(null)}
                  className="flex-1 h-12 rounded-xl border border-stone-300 font-semibold text-sm text-stone-700"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={submitting || anissaScore === null || adammeScore === null}
                  onClick={handleSaveRating}
                  className="flex-[2] h-12 rounded-xl bg-[#1E5631] text-white font-bold text-sm disabled:opacity-40 transition"
                >
                  {submitting ? "Saving to Sheet…" : "Save Rating"}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {toast && (
        <div className="fixed bottom-5 inset-x-4 max-w-sm mx-auto z-50 bg-stone-900 text-white text-xs font-semibold px-4 py-3 rounded-xl shadow-lg text-center">
          {toast}
        </div>
      )}
    </div>
  );
}
