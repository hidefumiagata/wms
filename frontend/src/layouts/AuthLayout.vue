<template>
  <div class="auth-layout">
    <div class="auth-layout__left">
      <div class="auth-layout__brand">
        <h1 class="auth-layout__title">WMS</h1>
        <p class="auth-layout__subtitle">Warehouse Management System</p>
      </div>
    </div>
    <div class="auth-layout__right">
      <div class="auth-layout__card">
        <RouterView />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-layout {
  display: flex;
  height: 100vh;

  &__left {
    flex: 1;
    background: linear-gradient(135deg, #1a56db 0%, #0e3c9e 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
  }

  &__brand {
    text-align: center;
  }

  &__title {
    font-size: 48px;
    font-weight: 700;
    letter-spacing: 4px;
    margin-bottom: 8px;
  }

  &__subtitle {
    font-size: 16px;
    opacity: 0.8;
    letter-spacing: 1px;
  }

  &__right {
    width: 480px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #fff;
  }

  &__card {
    width: 100%;
    padding: 0 48px;
  }
}
</style>
