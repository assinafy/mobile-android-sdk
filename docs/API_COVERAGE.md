# Assinafy v1 operations

Integration mappings for the [live API contract](https://api.assinafy.com.br/v1/docs/openapi.json).

The contract defines 147 operations. The SDK maps 146 integration operations; the embed-token exchange is performed only by the hosted signing page.

Account calls use an API key or scoped bearer token. Signer calls use the credential-free transport
and their access code. Download links carry their own token. Public OAuth uses PKCE without a client
secret; internal token-exchange grants are outside the Android client.

## Accounts

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/accounts/{accountId}` | `workspaces.get` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}` | `workspaces.update` | apiKeyAuth, bearerAuth |
| `DELETE /v1/accounts/{accountId}` | `workspaces.delete` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/theme` | `workspaces.getTheme` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/usage` | `workspaces.getUsage` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/logo` | `workspaces.getLogo` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/logo` | `workspaces.uploadLogo` | apiKeyAuth, bearerAuth |
| `DELETE /v1/accounts/{accountId}/logo` | `workspaces.deleteLogo` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts` | `workspaces.list` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts` | `workspaces.create` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/notification-policy` | `workspaces.getNotificationPolicy` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/notification-policy` | `workspaces.updateNotificationPolicy` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/stats` | `workspaces.getStats` | apiKeyAuth, bearerAuth |

## Documents

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/documents/{documentId}/activities` | `documents.activities` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/documents` | `documents.list` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/documents` | `documents.upload` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/documents/search` | `documents.search` | apiKeyAuth, bearerAuth |
| `GET /v1/documents/statuses` | `documents.getStatuses` | apiKeyAuth, bearerAuth |
| `GET /v1/documents/{documentId}` | `documents.details` | apiKeyAuth, bearerAuth, signerAccessCode |
| `DELETE /v1/documents/{documentId}` | `documents.delete` | apiKeyAuth, bearerAuth |
| `PATCH /v1/documents/{documentId}` | `documents.rename` | apiKeyAuth, bearerAuth |
| `GET /v1/documents/{documentId}/download/{artifactName}` | `documents.download` | apiKeyAuth, bearerAuth, signerAccessCode |
| `GET /v1/documents/{documentSignatureHash}/verify` | `documents.verify` | Public |
| `GET /v1/accounts/{accountId}/documents/{documentId}/tags` | `documents.listTags` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/documents/{documentId}/tags` | `documents.replaceTags` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/documents/{documentId}/tags` | `documents.addTags` | apiKeyAuth, bearerAuth |
| `DELETE /v1/accounts/{accountId}/documents/{documentId}/tags/{tagId}` | `documents.detachTag` | apiKeyAuth, bearerAuth |
| `GET /v1/documents/{documentId}/thumbnail` | `documents.thumbnail` | apiKeyAuth, bearerAuth, signerAccessCode |
| `GET /v1/documents/{documentId}/pages/{pageId}/download` | `documents.downloadPage` | apiKeyAuth, bearerAuth, signerAccessCode |
| `POST /v1/accounts/{accountId}/templates/{templateId}/documents` | `documents.createFromTemplate` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/templates/{templateId}/documents/estimate-cost` | `documents.estimateCostFromTemplate` | apiKeyAuth, bearerAuth |

## Assignments

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/assignments` | `assignments.list` | apiKeyAuth, bearerAuth |
| `POST /v1/documents/{documentId}/assignments` | `assignments.create` | apiKeyAuth, bearerAuth |
| `POST /v1/documents/{documentId}/assignments/estimate-cost` | `assignments.estimateCost` | apiKeyAuth, bearerAuth |
| `PUT /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/resend` | `assignments.resendNotification` | apiKeyAuth, bearerAuth |
| `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/estimate-resend-cost` | `assignments.estimateResendCost` | apiKeyAuth, bearerAuth |
| `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reset-expiration` | `assignments.resetExpiration` | apiKeyAuth, bearerAuth |
| `GET /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/identity-verification` | `assignments.getIdentityVerification` | apiKeyAuth, bearerAuth |
| `GET /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/identity-verification/images/{name}` | `assignments.downloadIdentityVerificationImage` | apiKeyAuth, bearerAuth |
| `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/identity-verification/review` | `assignments.reviewIdentityVerification` | apiKeyAuth, bearerAuth |
| `GET /v1/documents/{documentId}/assignments/{assignmentId}/whatsapp-notifications` | `assignments.listWhatsappNotifications` | apiKeyAuth, bearerAuth |

## Authentication

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `POST /v1/login` | `authentication.login` | Public |
| `PUT /v1/authentication/request-password-reset` | `authentication.requestPasswordReset` | Public |
| `PUT /v1/authentication/reset-password` | `authentication.resetPassword` | Public |
| `PUT /v1/authentication/change-password` | `authentication.changePassword` | apiKeyAuth, bearerAuth |
| `POST /v1/authentication/mfa/verify` | `authentication.verifyMfa` | Public |
| `GET /v1/users/self/mfa` | `authentication.listMfaMethods` | apiKeyAuth, bearerAuth |
| `POST /v1/users/self/mfa/totp` | `authentication.startTotpEnrollment` | apiKeyAuth, bearerAuth |
| `PUT /v1/users/self/mfa/totp/confirm` | `authentication.confirmTotpEnrollment` | apiKeyAuth, bearerAuth |
| `POST /v1/users/self/mfa/recovery-codes` | `authentication.regenerateRecoveryCodes` | apiKeyAuth, bearerAuth |
| `DELETE /v1/users/self/mfa/{customId}` | `authentication.removeMfaMethod` | apiKeyAuth, bearerAuth |
| `POST /v1/authentication/social-login` | `authentication.socialLogin` | Public |
| `POST /v1/auth/link-social-login` | `authentication.linkSocialLogin` | apiKeyAuth, bearerAuth |
| `GET /v1/users/api-keys` | `authentication.getApiKey` | apiKeyAuth, bearerAuth |
| `POST /v1/users/api-keys` | `authentication.createApiKey` | apiKeyAuth, bearerAuth |
| `DELETE /v1/users/api-keys` | `authentication.deleteApiKey` | apiKeyAuth, bearerAuth |

## Contracts

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/accounts/{accountId}/contracts` | `contracts.list` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/documents/{documentId}/contract` | `contracts.get` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/documents/{documentId}/contract` | `contracts.update` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/documents/{documentId}/contract/related` | `contracts.related` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/documents/{documentId}/contract/renew` | `contracts.renew` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/documents/{documentId}/contract/terminate` | `contracts.terminate` | apiKeyAuth, bearerAuth |

## Embedded Signing

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/accounts/{accountId}/embed-origins` | `embeddedSigning.listOrigins` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/embed-origins` | `embeddedSigning.addOrigin` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/embed-origins/{originId}` | `embeddedSigning.updateOrigin` | apiKeyAuth, bearerAuth |
| `DELETE /v1/accounts/{accountId}/embed-origins/{originId}` | `embeddedSigning.deleteOrigin` | apiKeyAuth, bearerAuth |
| `POST /v1/documents/{documentId}/assignments/{assignmentId}/signers/{signerId}/embed-sessions` | `embeddedSigning.createSession` | apiKeyAuth, bearerAuth |
| `POST /v1/embed/sessions/{token}/exchange` | `Hosted embed page only; integrations use embeddedSigning.createSession` | Public |

## Envelopes

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/accounts/{accountId}/envelopes` | `envelopes.list` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/envelopes` | `envelopes.create` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/envelopes/estimate-cost` | `envelopes.estimateCost` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/envelopes/{envelopeId}` | `envelopes.get` | apiKeyAuth, bearerAuth |
| `DELETE /v1/accounts/{accountId}/envelopes/{envelopeId}` | `envelopes.delete` | apiKeyAuth, bearerAuth |
| `PATCH /v1/accounts/{accountId}/envelopes/{envelopeId}` | `envelopes.update` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/envelopes/{envelopeId}/activities` | `envelopes.activities` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/envelopes/{envelopeId}/download/{artifactName}` | `envelopes.download` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/envelopes/{envelopeId}/cancel` | `envelopes.cancel` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/envelopes/{envelopeId}/signers/{signerId}/resend` | `envelopes.resendNotification` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/envelopes/{envelopeId}/documents` | `envelopes.addDocument` | apiKeyAuth, bearerAuth |
| `DELETE /v1/accounts/{accountId}/envelopes/{envelopeId}/documents/{documentId}` | `envelopes.removeDocument` | apiKeyAuth, bearerAuth |

## Fields

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/accounts/{accountId}/fields` | `fields.list` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/fields` | `fields.create` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/fields/{fieldId}` | `fields.get` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/fields/{fieldId}` | `fields.update` | apiKeyAuth, bearerAuth |
| `DELETE /v1/accounts/{accountId}/fields/{fieldId}` | `fields.delete` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/fields/{fieldId}/validate` | `fields.validate` | apiKeyAuth, bearerAuth, signerAccessCode |
| `POST /v1/accounts/{accountId}/fields/validate-multiple` | `fields.validateMultiple` | apiKeyAuth, bearerAuth, signerAccessCode |
| `GET /v1/field-types` | `fields.listTypes` | apiKeyAuth, bearerAuth |

## Users

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/users/self/notification-preferences` | `users.getNotificationPreferences` | apiKeyAuth, bearerAuth |
| `PUT /v1/users/self/notification-preferences` | `users.updateNotificationPreferences` | apiKeyAuth, bearerAuth |
| `GET /v1/users/self/push-preferences` | `users.getPushPreferences` | apiKeyAuth, bearerAuth |
| `PUT /v1/users/self/push-preferences` | `users.updatePushPreferences` | apiKeyAuth, bearerAuth |
| `GET /v1/users/self/stats` | `users.getStats` | apiKeyAuth, bearerAuth |
| `GET /v1/users/self` | `users.getCurrent` | apiKeyAuth, bearerAuth |

## OAuth

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `POST /v1/oauth/token` | `oauth.exchangeCode / refresh` | Public |
| `POST /v1/oauth/revoke` | `oauth.revoke` | Public |
| `GET /v1/oauth/userinfo` | `oauth.userInfo` | apiKeyAuth, bearerAuth |
| `GET /.well-known/oauth-protected-resource` | `oauth.protectedResourceMetadata` | Public |

## Signing

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/public/documents/{documentId}` | `documents.getPublic` | Public |
| `PUT /v1/public/documents/{documentId}/send-token` | `documents.sendAccessToken` | Public |
| `GET /v1/signers/self` | `signerDocuments.self` | signerAccessCode |
| `GET /v1/sign/theme` | `signerDocuments.getTheme` | signerAccessCode |
| `GET /v1/sign/logo` | `signerDocuments.getLogo` | signerAccessCode |
| `GET /v1/signers/identity-verification` | `signerDocuments.getIdentityVerification` | signerAccessCode |
| `POST /v1/signers/identity-verification` | `signerDocuments.startIdentityVerification` | signerAccessCode |
| `GET /v1/signers/{signerId}/document` | `signerDocuments.getCurrent` | signerAccessCode |
| `GET /v1/sign` | `signerDocuments.getAssignment` | signerAccessCode |
| `POST /v1/documents/{documentId}/assignments/{assignmentId}` | `signerDocuments.sign` | signerAccessCode |
| `PUT /v1/documents/{documentId}/assignments/{assignmentId}/reject` | `assignments.decline` | signerAccessCode |
| `PUT /v1/signers/documents/sign-multiple` | `signerDocuments.signMultiple` | signerAccessCode |
| `PUT /v1/signers/documents/decline-multiple` | `signerDocuments.declineMultiple` | signerAccessCode |
| `POST /v1/verify` | `signerDocuments.verifyEmail` | signerAccessCode |
| `PUT /v1/documents/{documentId}/signers/confirm-data` | `documents.confirmSignerData` | signerAccessCode |
| `PUT /v1/signers/accept-terms` | `signerDocuments.acceptTerms` | signerAccessCode |
| `POST /v1/signature` | `signerDocuments.uploadSignature` | signerAccessCode |
| `GET /v1/signature/{signatureType}` | `signerDocuments.downloadSignature` | signerAccessCode |
| `GET /v1/signers/{signerId}/documents` | `signerDocuments.list` | apiKeyAuth, bearerAuth, signerAccessCode |
| `GET /v1/signers/{signerId}/documents/search` | `signerDocuments.search` | apiKeyAuth, bearerAuth, signerAccessCode |
| `GET /v1/signers/{signerId}/documents/{documentId}/download/{artifactName}` | `signerDocuments.download` | signerAccessCode |
| `POST /v1/sign/certified-copy` | `signerDocuments.resendCertifiedCopy` | signerAccessCode |
| `GET /v1/signers/downloads/{token}` | `signerDocuments.downloadCertifiedCopy` | Public |
| `POST /v1/signers/downloads/{token}/resend` | `signerDocuments.resendCertifiedCopyByToken` | Public |

## Signers

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/accounts/{accountId}/signers` | `signers.list` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/signers` | `signers.create` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/signers/{signerId}` | `signers.get` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/signers/{signerId}` | `signers.update` | apiKeyAuth, bearerAuth |
| `DELETE /v1/accounts/{accountId}/signers/{signerId}` | `signers.delete` | apiKeyAuth, bearerAuth |

## Tags

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/accounts/{accountId}/tags` | `tags.list` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/tags` | `tags.create` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/tags/{tagId}` | `tags.update` | apiKeyAuth, bearerAuth |
| `DELETE /v1/accounts/{accountId}/tags/{tagId}` | `tags.delete` | apiKeyAuth, bearerAuth |

## Templates

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/accounts/{accountId}/templates` | `templates.list` | apiKeyAuth, bearerAuth |

## Webhooks

| Operation | SDK entry point | API security schemes |
|---|---|---|
| `GET /v1/accounts/{accountId}/webhooks/subscriptions` | `webhooks.get` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/webhooks/subscriptions` | `webhooks.register` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/webhooks/inactivate` | `webhooks.inactivate` | apiKeyAuth, bearerAuth |
| `GET /v1/webhooks/event-types` | `webhooks.listEventTypes` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/webhooks` | `webhooks.listDispatches` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/webhooks/{historyId}/retry` | `webhooks.retryDispatch` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/webhooks/endpoints` | `webhooks.listEndpoints` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/webhooks/endpoints` | `webhooks.createEndpoint` | apiKeyAuth, bearerAuth |
| `DELETE /v1/accounts/{accountId}/webhooks/endpoints` | `webhooks.deleteEndpointByUrl` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}` | `webhooks.getEndpoint` | apiKeyAuth, bearerAuth |
| `PUT /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}` | `webhooks.updateEndpoint` | apiKeyAuth, bearerAuth |
| `DELETE /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}` | `webhooks.deleteEndpoint` | apiKeyAuth, bearerAuth |
| `GET /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret` | `webhooks.getEndpointSecret` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/secret/rotate` | `webhooks.rotateEndpointSecret` | apiKeyAuth, bearerAuth |
| `POST /v1/accounts/{accountId}/webhooks/endpoints/{endpointId}/test` | `webhooks.testEndpoint` | apiKeyAuth, bearerAuth |

## Additional integration behavior

`templates.get` retains the deployed single-template read although it is absent from OpenAPI.
Certificate start/complete routes require the Web PKI browser extension; use the hosted signing
page for A1/A3 completion. Public request/response examples, overloads and compatibility extensions
are described in [API_REFERENCE.md](API_REFERENCE.md). Test prerequisites and live-test boundaries
are described in [TESTING.md](TESTING.md).
