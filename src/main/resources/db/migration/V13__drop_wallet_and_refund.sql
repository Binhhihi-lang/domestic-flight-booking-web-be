-- =====================================================================
-- V13__drop_wallet_and_refund.sql
-- Chốt 15/10/2026: bỏ ví khách hàng + hoàn tiền tự động.
--
-- Hủy vé nay chỉ đổi trạng thái booking, KHÔNG cộng tiền vào ví và KHÔNG
-- tính số tiền hoàn → bỏ 2 cột refund ở bookings, 2 bảng ví, và quyền
-- WALLET_READ_SELF (endpoint /wallet không còn).
-- =====================================================================

-- Sổ giao dịch tham chiếu wallets + bookings → xóa trước
DROP TABLE wallet_transactions;
DROP TABLE wallets;

-- Bỏ cột hoàn tiền (kèm CHECK constraint tự rơi theo cột)
ALTER TABLE bookings DROP COLUMN applied_refund_percent;
ALTER TABLE bookings DROP COLUMN refund_amount;

-- Quyền chỉ phục vụ ví
DELETE FROM role_permissions
WHERE permission_id IN (SELECT id FROM permissions WHERE permission_key = 'WALLET_READ_SELF');

DELETE FROM permissions WHERE permission_key = 'WALLET_READ_SELF';
