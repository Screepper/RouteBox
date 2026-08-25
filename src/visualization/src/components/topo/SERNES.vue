<template>
  <div class="page-container">
    <div class="features-container">
      <div class="feature-card" v-for="(feature, index) in features" :key="index">
        <div class="feature-icon">
          <img :src="feature.icon" :alt="feature.title" />
        </div>
        <h3 class="feature-title">{{ $t(feature.title) }}</h3>
        <p class="feature-description">{{ $t(feature.description) }}</p>
      </div>
    </div>

    <div class="button-container">
      <el-button type="primary" class="navigate-button" @click="handleNavigate">
        {{ $t('pages.sernes.navigate') }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getSvg } from '@/utils/FileUtil';
import type { Feature } from '@/ifaces/topo';

const features: Feature[] = [
  {
    title: 'pages.sernes.features.0.title',
    description: 'pages.sernes.features.0.description',
    icon: getSvg('sernes/topology')
  },
  {
    title: 'pages.sernes.features.1.title',
    description: 'pages.sernes.features.1.description',
    icon: getSvg('sernes/semi-physical')
  },
  {
    title: 'pages.sernes.features.2.title',
    description: 'pages.sernes.features.2.description',
    icon: getSvg('sernes/custom')
  },
  {
    title: 'pages.sernes.features.3.title',
    description: 'pages.sernes.features.3.description',
    icon: getSvg('sernes/collection')
  },
  {
    title: 'pages.sernes.features.4.title',
    description: 'pages.sernes.features.4.description',
    icon: getSvg('sernes/terminal')
  },
  {
    title: 'pages.sernes.features.5.title',
    description: 'pages.sernes.features.5.description',
    icon: getSvg('sernes/construction')
  }
];

const handleNavigate = () => window.open('https://www.sernes.cn/', '_blank');
</script>

<style lang="scss" scoped>
.page-container {
  padding: 10px;
  background-color: var(--bg-color);
  color: var(--text-primary-color);

  .features-container {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 30px;
    padding: 0 20px;
    max-width: 1400px;
    margin: 15px auto;

    .feature-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      background-color: var(--card-bg-color);
      border-radius: 12px;
      padding: 15px;
      box-shadow: var(--box-shadow);
      transition: all 0.3s ease;
      border: 1px solid var(--border-color);

      &:hover {
        transform: translateY(-5px);
        box-shadow: none;
      }

      .feature-icon {
        width: 60px;
        height: 60px;
        margin-bottom: 15px;
        display: flex;
        align-items: center;
        justify-content: center;

        img {
          width: 100%;
          height: 100%;
          object-fit: contain;
        }
      }

      .feature-title {
        font-size: 18px;
        font-weight: 700;
        margin-bottom: 10px;
        color: var(--text-primary-color);
      }

      .feature-description {
        font-size: 14px;
        line-height: 1.5;
        color: var(--text-secondary-color);
      }
    }
  }

  .button-container {
    display: flex;
    justify-content: center;
    margin: 30px 0 20px 0;

    .navigate-button {
      font-size: 18px;
      padding: 20px 25px;
      border-radius: 15px;
      background-color: var(--primary-color);
      color: var(--text-reverse-color);
      border: none;

      &:hover {
        background-color: var(--secondary-color);
        color: var(--text-primary-color);
      }
    }
  }
}
</style>