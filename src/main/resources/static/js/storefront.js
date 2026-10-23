'use strict';
document.querySelectorAll('.fallback-image').forEach(image => {
  const fallback = () => { if (!image.src.endsWith('/images/placeholder.svg')) image.src = '/images/placeholder.svg'; };
  image.addEventListener('error', fallback);
  if (image.complete && image.naturalWidth === 0) fallback();
});
document.querySelectorAll('[data-password]').forEach(button => {
  button.addEventListener('click', () => {
    const input = document.getElementById(button.dataset.password);
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.textContent = show ? 'Ẩn' : 'Hiện';
    button.setAttribute('aria-label', show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
  });
});
document.querySelectorAll('[data-review-dialog-open]').forEach(button => {
  const form = button.closest('form');
  const dialog = form?.querySelector('.review-rating-dialog');
  if (!form || !dialog) return;
  if (typeof dialog.showModal !== 'function') {
    dialog.setAttribute('open', '');
    button.hidden = true;
    return;
  }
  button.addEventListener('click', () => {
    const comment = form.querySelector('[name="comment"]');
    if (!comment.reportValidity()) return;
    dialog.showModal();
    dialog.querySelector('[name="rating"]')?.focus({ preventScroll: true });
  });
  dialog.querySelector('[data-review-dialog-close]')?.addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', event => {
    if (event.target === dialog) dialog.close();
  });
});
document.querySelectorAll('.review-rating-options').forEach(group => {
  const options = [...group.querySelectorAll('label')];
  const paint = value => options.forEach(label => {
    const input = label.querySelector('input');
    const star = label.querySelector('span');
    const highlighted = Number(input.value) <= value;
    label.classList.toggle('is-highlighted', highlighted);
    star.textContent = highlighted ? '★' : '☆';
  });
  options.forEach(label => {
    const input = label.querySelector('input');
    label.addEventListener('mouseenter', () => paint(Number(input.value)));
    input.addEventListener('change', () => paint(Number(input.value)));
  });
  group.addEventListener('mouseleave', () => paint(Number(group.querySelector('input:checked')?.value || 0)));
  paint(Number(group.querySelector('input:checked')?.value || 0));
});
document.querySelectorAll('[data-review-edit-toggle]').forEach(button => {
  const form = button.form || document.getElementById('review-edit-form');
  if (!form) return;
  const saveButton = document.querySelector('[data-review-edit-save]');
  button.addEventListener('click', event => {
    event.preventDefault();
    form.classList.add('is-editing');
    const comment = form.querySelector('[name="comment"]');
    if (comment) {
      comment.disabled = false;
      comment.readOnly = false;
      comment.focus();
    }
    form.querySelectorAll('[name="rating"]').forEach(input => { input.disabled = false; });
    button.hidden = true;
    if (saveButton) saveButton.hidden = false;
  });
});
const checkoutForm = document.getElementById('checkout-form');
if (checkoutForm) {
  checkoutForm.addEventListener('input', () => {
    const confirmed = document.getElementById('confirmed-quote');
    const changed = document.getElementById('quote-changed');
    if (confirmed) confirmed.hidden = true;
    if (changed) changed.hidden = false;
  });
  const address = checkoutForm.querySelector('[name="address"]');
  const updateAddress = () => { address.required = checkoutForm.querySelector('[name="deliveryMethod"]:checked')?.value !== 'pickup'; };
  checkoutForm.querySelectorAll('[name="deliveryMethod"]').forEach(input => input.addEventListener('change', updateAddress));
  updateAddress();
}
document.querySelectorAll('[data-user-manage-open]').forEach(trigger => {
  const dialog = document.getElementById(trigger.getAttribute('aria-controls'));
  if (!dialog || typeof dialog.showModal !== 'function') return;
  trigger.addEventListener('click', () => {
    dialog.showModal();
    trigger.setAttribute('aria-expanded', 'true');
    dialog.querySelector('[data-user-manage-close]')?.focus({ preventScroll: true });
  });
  dialog.querySelector('[data-user-manage-close]')?.addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', event => {
    if (event.target === dialog) dialog.close();
  });
  dialog.addEventListener('close', () => {
    trigger.setAttribute('aria-expanded', 'false');
    trigger.focus({ preventScroll: true });
  });
});
document.querySelector('#place-order')?.addEventListener('submit', event => {
  const button = event.currentTarget.querySelector('button');
  button.disabled = true;
  button.textContent = 'Đang ghi nhận đơn hàng…';
});
window.addEventListener('pageshow', () => {
  const button = document.querySelector('#place-order button');
  if (button) { button.disabled = false; button.textContent = 'Xác nhận đặt hàng →'; }
});
