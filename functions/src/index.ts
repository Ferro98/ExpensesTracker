import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging, MulticastMessage } from "firebase-admin/messaging";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";

initializeApp();

/**
 * Notifies the other group member whenever a shared expense is added.
 *
 * The message is data-only (no "notification" key): the Android client's
 * SharedExpenseFcmService.onMessageReceived builds the title/body itself, in the
 * recipient's own device language, from these raw fields - this function has no
 * reliable way to know each recipient's locale, so it never tries to.
 */
export const onSharedExpenseCreated = onDocumentCreated(
  "groups/{groupId}/expenses/{expenseId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const expense = snapshot.data();
    if (!expense?.isShared) return;

    const paidByUid: string | undefined = expense.paidByUid;
    if (!paidByUid) return;

    const { groupId } = event.params;
    const db = getFirestore();

    const groupSnap = await db.collection("groups").doc(groupId).get();
    const group = groupSnap.data();
    if (!group) return;

    const memberUids: string[] = group.memberUids ?? [];
    const memberNames: Record<string, string> = group.memberNames ?? {};
    const recipientUids = memberUids.filter((uid) => uid !== paidByUid);
    if (recipientUids.length === 0) return;

    const tokenDocs = await Promise.all(
      recipientUids.map((uid) => db.collection("users").doc(uid).collection("fcmTokens").get())
    );
    const tokens = tokenDocs.flatMap((snap) => snap.docs.map((doc) => doc.id));
    if (tokens.length === 0) return;

    const payerName = memberNames[paidByUid] ?? "Partner";
    const message: MulticastMessage = {
      tokens,
      data: {
        payerName,
        categoryName: String(expense.categoryName ?? ""),
        categoryIcon: String(expense.categoryIcon ?? ""),
        amount: String(expense.amountInBaseCurrency ?? expense.amount ?? 0),
        currencyCode: String(expense.currencyCode ?? "EUR"),
      },
      android: { priority: "high" },
    };

    const response = await getMessaging().sendEachForMulticast(message);

    // Tokens that are no longer valid (app uninstalled, token rotated without a fresh
    // registration yet) would otherwise sit forever and get retried on every future
    // notification - prune them so the token list stays accurate.
    const staleTokens: string[] = [];
    response.responses.forEach((result, index) => {
      if (!result.success) {
        const code = result.error?.code;
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-argument"
        ) {
          staleTokens.push(tokens[index]);
        } else {
          logger.warn("FCM send failed", { token: tokens[index], error: result.error });
        }
      }
    });

    if (staleTokens.length > 0) {
      await Promise.all(
        recipientUids.map((uid) => {
          const tokensRef = db.collection("users").doc(uid).collection("fcmTokens");
          return Promise.all(staleTokens.map((token) => tokensRef.doc(token).delete()));
        })
      );
    }
  }
);
