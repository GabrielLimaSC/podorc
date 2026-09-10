/**
 * Wire contracts for the two orchestrator Kafka topics.
 *
 * <ul>
 *   <li>{@link com.podorc.contracts.message.InboundMessage} — {@code orchestrator.inbound} (spec §2.2)</li>
 *   <li>{@link com.podorc.contracts.message.OutboundMessage} — {@code orchestrator.outbound} (spec §2.3)</li>
 *   <li>{@link com.podorc.contracts.message.OutboundType}, {@link com.podorc.contracts.message.TaskState}
 *       — closed enums, serialized as their snake_case wire names</li>
 * </ul>
 *
 * <p>Fields carry explicit {@code @JsonProperty} snake_case names, so the format does not depend
 * on mapper naming strategy. Required fields are checked in the record constructors and fail with
 * {@code IllegalArgumentException}. Use {@link com.podorc.contracts.MessageJson#MAPPER} to read
 * and write these types.
 *
 * <p>The versioned JSON Schemas under {@code resources/schema/} are the strict contract gate
 * ({@code additionalProperties: false}); the runtime mapper is deliberately lenient about unknown
 * fields so a new optional field does not break an older consumer.
 */
package com.podorc.contracts.message;
