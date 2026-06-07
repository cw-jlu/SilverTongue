import { useState, useEffect, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Eye, EyeOff, MessageCircle, Sparkles } from 'lucide-react';
import api from '../api/client';
import { getWeChatOAuthUrl } from '../config/wechat';

const Pupil = ({
  size = 12,
  maxDistance = 5,
  pupilColor = "black",
  forceLookX,
  forceLookY
}) => {
  const [mouseX, setMouseX] = useState(0);
  const [mouseY, setMouseY] = useState(0);
  const pupilRef = useRef(null);

  useEffect(() => {
    const handleMouseMove = (e) => {
      setMouseX(e.clientX);
      setMouseY(e.clientY);
    };
    window.addEventListener("mousemove", handleMouseMove);
    return () => window.removeEventListener("mousemove", handleMouseMove);
  }, []);

  const calculatePupilPosition = () => {
    if (!pupilRef.current) return { x: 0, y: 0 };
    if (forceLookX !== undefined && forceLookY !== undefined) {
      return { x: forceLookX, y: forceLookY };
    }
    const pupil = pupilRef.current.getBoundingClientRect();
    const pupilCenterX = pupil.left + pupil.width / 2;
    const pupilCenterY = pupil.top + pupil.height / 2;
    const deltaX = mouseX - pupilCenterX;
    const deltaY = mouseY - pupilCenterY;
    const distance = Math.min(Math.sqrt(deltaX ** 2 + deltaY ** 2), maxDistance);
    const angle = Math.atan2(deltaY, deltaX);
    return {
      x: Math.cos(angle) * distance,
      y: Math.sin(angle) * distance
    };
  };

  const pupilPosition = calculatePupilPosition();

  return (
    <div
      ref={pupilRef}
      style={{
        width: `${size}px`,
        height: `${size}px`,
        backgroundColor: pupilColor,
        borderRadius: '50%',
        transform: `translate(${pupilPosition.x}px, ${pupilPosition.y}px)`,
        transition: 'transform 0.1s ease-out',
      }}
    />
  );
};

const EyeBall = ({
  size = 48,
  pupilSize = 16,
  maxDistance = 10,
  eyeColor = "white",
  pupilColor = "black",
  isBlinking = false,
  forceLookX,
  forceLookY
}) => {
  const [mouseX, setMouseX] = useState(0);
  const [mouseY, setMouseY] = useState(0);
  const eyeRef = useRef(null);

  useEffect(() => {
    const handleMouseMove = (e) => {
      setMouseX(e.clientX);
      setMouseY(e.clientY);
    };
    window.addEventListener("mousemove", handleMouseMove);
    return () => window.removeEventListener("mousemove", handleMouseMove);
  }, []);

  const calculatePupilPosition = () => {
    if (!eyeRef.current) return { x: 0, y: 0 };
    if (forceLookX !== undefined && forceLookY !== undefined) {
      return { x: forceLookX, y: forceLookY };
    }
    const eye = eyeRef.current.getBoundingClientRect();
    const eyeCenterX = eye.left + eye.width / 2;
    const eyeCenterY = eye.top + eye.height / 2;
    const deltaX = mouseX - eyeCenterX;
    const deltaY = mouseY - eyeCenterY;
    const distance = Math.min(Math.sqrt(deltaX ** 2 + deltaY ** 2), maxDistance);
    const angle = Math.atan2(deltaY, deltaX);
    return {
      x: Math.cos(angle) * distance,
      y: Math.sin(angle) * distance
    };
  };

  const pupilPosition = calculatePupilPosition();

  return (
    <div
      ref={eyeRef}
      style={{
        width: `${size}px`,
        height: isBlinking ? '2px' : `${size}px`,
        backgroundColor: eyeColor,
        borderRadius: '50%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
        transition: 'height 0.15s ease-in-out'
      }}
    >
      {!isBlinking && (
        <div
          style={{
            width: `${pupilSize}px`,
            height: `${pupilSize}px`,
            backgroundColor: pupilColor,
            borderRadius: '50%',
            transform: `translate(${pupilPosition.x}px, ${pupilPosition.y}px)`,
            transition: 'transform 0.1s ease-out'
          }}
        />
      )}
    </div>
  );
};

export default function Login() {
  const [showPassword, setShowPassword] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const [mouseX, setMouseX] = useState(0);
  const [mouseY, setMouseY] = useState(0);
  const [isPurpleBlinking, setIsPurpleBlinking] = useState(false);
  const [isBlackBlinking, setIsBlackBlinking] = useState(false);
  const [isTyping, setIsTyping] = useState(false);
  const [isLookingAtEachOther, setIsLookingAtEachOther] = useState(false);
  const [isPurplePeeking, setIsPurplePeeking] = useState(false);

  const purpleRef = useRef(null);
  const blackRef = useRef(null);
  const yellowRef = useRef(null);
  const orangeRef = useRef(null);

  const navigate = useNavigate();

  useEffect(() => {
    const saved = localStorage.getItem('rememberedUsername');
    if (saved) {
      setUsername(saved);
      setRememberMe(true);
    }
  }, []);

  useEffect(() => {
    const handleMouseMove = (e) => {
      setMouseX(e.clientX);
      setMouseY(e.clientY);
    };
    window.addEventListener("mousemove", handleMouseMove);
    return () => window.removeEventListener("mousemove", handleMouseMove);
  }, []);

  useEffect(() => {
    const getRandomBlinkInterval = () => Math.random() * 4000 + 3000;
    const scheduleBlink = () => {
      return setTimeout(() => {
        setIsPurpleBlinking(true);
        setTimeout(() => {
          setIsPurpleBlinking(false);
          purpleBlinkTimeout = scheduleBlink();
        }, 150);
      }, getRandomBlinkInterval());
    };
    let purpleBlinkTimeout = scheduleBlink();
    return () => clearTimeout(purpleBlinkTimeout);
  }, []);

  useEffect(() => {
    const getRandomBlinkInterval = () => Math.random() * 4000 + 3000;
    const scheduleBlink = () => {
      return setTimeout(() => {
        setIsBlackBlinking(true);
        setTimeout(() => {
          setIsBlackBlinking(false);
          blackBlinkTimeout = scheduleBlink();
        }, 150);
      }, getRandomBlinkInterval());
    };
    let blackBlinkTimeout = scheduleBlink();
    return () => clearTimeout(blackBlinkTimeout);
  }, []);

  useEffect(() => {
    if (isTyping) {
      setIsLookingAtEachOther(true);
      const timer = setTimeout(() => {
        setIsLookingAtEachOther(false);
      }, 800);
      return () => clearTimeout(timer);
    } else {
      setIsLookingAtEachOther(false);
    }
  }, [isTyping]);

  useEffect(() => {
    if (password.length > 0 && showPassword) {
      const schedulePeek = () => {
        return setTimeout(() => {
          setIsPurplePeeking(true);
          setTimeout(() => {
            setIsPurplePeeking(false);
            peekInterval = schedulePeek();
          }, 800);
        }, Math.random() * 3000 + 2000);
      };
      let peekInterval = schedulePeek();
      return () => clearTimeout(peekInterval);
    } else {
      setIsPurplePeeking(false);
    }
  }, [password, showPassword]);

  const calculatePosition = (ref) => {
    if (!ref.current) return { faceX: 0, faceY: 0, bodySkew: 0 };
    const rect = ref.current.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 3;
    const deltaX = mouseX - centerX;
    const deltaY = mouseY - centerY;
    return {
      faceX: Math.max(-15, Math.min(15, deltaX / 20)),
      faceY: Math.max(-10, Math.min(10, deltaY / 30)),
      bodySkew: Math.max(-6, Math.min(6, -deltaX / 120))
    };
  };

  const purplePos = calculatePosition(purpleRef);
  const blackPos = calculatePosition(blackRef);
  const yellowPos = calculatePosition(yellowRef);
  const orangePos = calculatePosition(orangeRef);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    if (!username || !password) {
      setError('请输入用户名和密码');
      setLoading(false);
      return;
    }

    try {
      const res = await api.post('/user/login', { username, password });
      if (rememberMe) {
        localStorage.setItem('rememberedUsername', username);
      } else {
        localStorage.removeItem('rememberedUsername');
      }
      localStorage.setItem('token', res.data.token);
      if (res.data?.user?.id) {
        localStorage.setItem('userId', res.data.user.id);
      }
      navigate('/');
    } catch (err) {
      setError(err?.message || '登录失败，请检查用户名或密码');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
      minHeight: '100vh',
      backgroundColor: '#f9fafb',
      fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    }}>
      {/* 动画角色板块 (左侧/顶部) */}
      <div style={{
        backgroundColor: '#0c0f1d',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        padding: '40px',
        color: '#fff',
        position: 'relative',
        overflow: 'hidden',
        minHeight: '450px'
      }}>
        {/* LOGO */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', zIndex: 10 }}>
          <div style={{
            width: '32px',
            height: '32px',
            borderRadius: '8px',
            backgroundColor: 'rgba(255,255,255,0.1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <Sparkles size={16} color="#6C3FF5" />
          </div>
          <span style={{ fontSize: '18px', fontWeight: 'bold', letterSpacing: '0.5px' }}>SilverTongue</span>
        </div>

        {/* 角色容器 */}
        <div style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'end',
          height: '380px',
          position: 'relative',
          zIndex: 5
        }}>
          <div style={{ position: 'relative', width: '450px', height: '320px' }}>
            {/* 紫色高个子小人 */}
            <div
              ref={purpleRef}
              style={{
                position: 'absolute',
                bottom: 0,
                left: '60px',
                width: '140px',
                height: (isTyping || (password.length > 0 && !showPassword)) ? '340px' : '310px',
                backgroundColor: '#6C3FF5',
                borderRadius: '8px 8px 0 0',
                zIndex: 1,
                transform: (password.length > 0 && showPassword)
                  ? 'skewX(0deg)'
                  : (isTyping || (password.length > 0 && !showPassword))
                    ? `skewX(${(purplePos.bodySkew || 0) - 12}deg) translateX(30px)`
                    : `skewX(${purplePos.bodySkew || 0}deg)`,
                transformOrigin: 'bottom center',
                transition: 'all 0.6s cubic-bezier(0.25, 0.8, 0.25, 1)'
              }}
            >
              {/* 眼睛 */}
              <div style={{
                position: 'absolute',
                display: 'flex',
                gap: '24px',
                left: (password.length > 0 && showPassword) ? '25px' : isLookingAtEachOther ? '45px' : `${40 + purplePos.faceX}px`,
                top: (password.length > 0 && showPassword) ? '30px' : isLookingAtEachOther ? '50px' : `${30 + purplePos.faceY}px`,
                transition: 'all 0.6s cubic-bezier(0.25, 0.8, 0.25, 1)'
              }}>
                <EyeBall size={14} pupilSize={6} maxDistance={4} eyeColor="white" pupilColor="#2D2D2D" isBlinking={isPurpleBlinking}
                  forceLookX={(password.length > 0 && showPassword) ? (isPurplePeeking ? 3 : -3) : isLookingAtEachOther ? 2 : undefined}
                  forceLookY={(password.length > 0 && showPassword) ? (isPurplePeeking ? 4 : -3) : isLookingAtEachOther ? 3 : undefined} />
                <EyeBall size={14} pupilSize={6} maxDistance={4} eyeColor="white" pupilColor="#2D2D2D" isBlinking={isPurpleBlinking}
                  forceLookX={(password.length > 0 && showPassword) ? (isPurplePeeking ? 3 : -3) : isLookingAtEachOther ? 2 : undefined}
                  forceLookY={(password.length > 0 && showPassword) ? (isPurplePeeking ? 4 : -3) : isLookingAtEachOther ? 3 : undefined} />
              </div>
            </div>

            {/* 黑色细长小人 */}
            <div
              ref={blackRef}
              style={{
                position: 'absolute',
                bottom: 0,
                left: '190px',
                width: '100px',
                height: '240px',
                backgroundColor: '#2D2D2D',
                borderRadius: '8px 8px 0 0',
                zIndex: 2,
                transform: (password.length > 0 && showPassword)
                  ? 'skewX(0deg)'
                  : isLookingAtEachOther
                    ? `skewX(${(blackPos.bodySkew || 0) * 1.5 + 10}deg) translateX(15px)`
                    : (isTyping || (password.length > 0 && !showPassword))
                      ? `skewX(${(blackPos.bodySkew || 0) * 1.5}deg)`
                      : `skewX(${blackPos.bodySkew || 0}deg)`,
                transformOrigin: 'bottom center',
                transition: 'all 0.6s cubic-bezier(0.25, 0.8, 0.25, 1)'
              }}
            >
              {/* 眼睛 */}
              <div style={{
                position: 'absolute',
                display: 'flex',
                gap: '16px',
                left: (password.length > 0 && showPassword) ? '12px' : isLookingAtEachOther ? '28px' : `${22 + blackPos.faceX}px`,
                top: (password.length > 0 && showPassword) ? '24px' : isLookingAtEachOther ? '12px' : `${26 + blackPos.faceY}px`,
                transition: 'all 0.6s cubic-bezier(0.25, 0.8, 0.25, 1)'
              }}>
                <EyeBall size={13} pupilSize={5} maxDistance={3} eyeColor="white" pupilColor="#2D2D2D" isBlinking={isBlackBlinking}
                  forceLookX={(password.length > 0 && showPassword) ? -3 : isLookingAtEachOther ? 0 : undefined}
                  forceLookY={(password.length > 0 && showPassword) ? -3 : isLookingAtEachOther ? -3 : undefined} />
                <EyeBall size={13} pupilSize={5} maxDistance={3} eyeColor="white" pupilColor="#2D2D2D" isBlinking={isBlackBlinking}
                  forceLookX={(password.length > 0 && showPassword) ? -3 : isLookingAtEachOther ? 0 : undefined}
                  forceLookY={(password.length > 0 && showPassword) ? -3 : isLookingAtEachOther ? -3 : undefined} />
              </div>
            </div>

            {/* 橙色半圆形小人 */}
            <div
              ref={orangeRef}
              style={{
                position: 'absolute',
                bottom: 0,
                left: '0px',
                width: '180px',
                height: '160px',
                backgroundColor: '#FF9B6B',
                borderRadius: '90px 90px 0 0',
                zIndex: 3,
                transform: (password.length > 0 && showPassword) ? 'skewX(0deg)' : `skewX(${orangePos.bodySkew || 0}deg)`,
                transformOrigin: 'bottom center',
                transition: 'all 0.6s cubic-bezier(0.25, 0.8, 0.25, 1)'
              }}
            >
              {/* 眼睛 */}
              <div style={{
                position: 'absolute',
                display: 'flex',
                gap: '24px',
                left: (password.length > 0 && showPassword) ? '40px' : `${62 + (orangePos.faceX || 0)}px`,
                top: (password.length > 0 && showPassword) ? '70px' : `${72 + (orangePos.faceY || 0)}px`,
                transition: 'all 0.2s ease-out'
              }}>
                <Pupil size={10} maxDistance={4} pupilColor="#2D2D2D" forceLookX={(password.length > 0 && showPassword) ? -4 : undefined} forceLookY={(password.length > 0 && showPassword) ? -3 : undefined} />
                <Pupil size={10} maxDistance={4} pupilColor="#2D2D2D" forceLookX={(password.length > 0 && showPassword) ? -4 : undefined} forceLookY={(password.length > 0 && showPassword) ? -3 : undefined} />
              </div>
            </div>

            {/* 黄色半长形小人 */}
            <div
              ref={yellowRef}
              style={{
                position: 'absolute',
                bottom: 0,
                left: '250px',
                width: '110px',
                height: '185px',
                backgroundColor: '#E8D754',
                borderRadius: '55px 55px 0 0',
                zIndex: 4,
                transform: (password.length > 0 && showPassword) ? 'skewX(0deg)' : `skewX(${yellowPos.bodySkew || 0}deg)`,
                transformOrigin: 'bottom center',
                transition: 'all 0.6s cubic-bezier(0.25, 0.8, 0.25, 1)'
              }}
            >
              {/* 眼睛 */}
              <div style={{
                position: 'absolute',
                display: 'flex',
                gap: '16px',
                left: (password.length > 0 && showPassword) ? '18px' : `${40 + (yellowPos.faceX || 0)}px`,
                top: (password.length > 0 && showPassword) ? '30px' : `${32 + (yellowPos.faceY || 0)}px`,
                transition: 'all 0.2s ease-out'
              }}>
                <Pupil size={10} maxDistance={4} pupilColor="#2D2D2D" forceLookX={(password.length > 0 && showPassword) ? -4 : undefined} forceLookY={(password.length > 0 && showPassword) ? -3 : undefined} />
                <Pupil size={10} maxDistance={4} pupilColor="#2D2D2D" forceLookX={(password.length > 0 && showPassword) ? -4 : undefined} forceLookY={(password.length > 0 && showPassword) ? -3 : undefined} />
              </div>
              {/* 嘴巴 */}
              <div style={{
                position: 'absolute',
                width: '40px',
                height: '4px',
                backgroundColor: '#2D2D2D',
                borderRadius: '2px',
                left: (password.length > 0 && showPassword) ? '10px' : `${30 + (yellowPos.faceX || 0)}px`,
                top: '70px',
                transition: 'all 0.2s ease-out'
              }} />
            </div>
          </div>
        </div>

        {/* 底部政策 */}
        <div style={{ display: 'flex', gap: '20px', fontSize: '12px', color: 'rgba(255,255,255,0.4)', zIndex: 10 }}>
          <span>隐私政策</span>
          <span>服务条款</span>
          <span>联系我们</span>
        </div>

        {/* 背景圆弧点缀 */}
        <div style={{
          position: 'absolute',
          width: '200px',
          height: '200px',
          borderRadius: '50%',
          backgroundColor: 'rgba(255,255,255,0.02)',
          filter: 'blur(40px)',
          top: '20%',
          right: '10%'
        }} />
      </div>

      {/* 登录表单板块 (右侧/底部) */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '40px',
        backgroundColor: '#ffffff'
      }}>
        <div style={{ width: '100%', maxWidth: '380px' }}>
          {/* 标题 */}
          <div style={{ textAlign: 'center', marginBottom: '32px' }}>
            <h1 style={{ fontSize: '28px', fontWeight: 'bold', color: '#111827', margin: '0 0 8px' }}>欢迎回来!</h1>
            <p style={{ fontSize: '14px', color: '#6b7280', margin: 0 }}>探索英语口语的魅力，即刻开启对练吧</p>
          </div>

          {/* 表单 */}
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            {/* 用户名 */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label style={{ fontSize: '13px', fontWeight: 600, color: '#374151' }}>用户名</label>
              <input
                type="text"
                placeholder="请输入用户名"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                onFocus={() => setIsTyping(true)}
                onBlur={() => setIsTyping(false)}
                required
                style={{
                  height: '46px',
                  padding: '0 14px',
                  border: '1px solid #e5e7eb',
                  borderRadius: '8px',
                  fontSize: '14px',
                  outline: 'none',
                  margin: 0
                }}
              />
            </div>

            {/* 密码 */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label style={{ fontSize: '13px', fontWeight: 600, color: '#374151' }}>密码</label>
              <div style={{ position: 'relative' }}>
                <input
                  type={showPassword ? "text" : "password"}
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onFocus={() => setIsTyping(true)}
                  onBlur={() => setIsTyping(false)}
                  required
                  style={{
                    height: '46px',
                    width: '100%',
                    padding: '0 40px 0 14px',
                    border: '1px solid #e5e7eb',
                    borderRadius: '8px',
                    fontSize: '14px',
                    outline: 'none',
                    margin: 0
                  }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  style={{
                    position: 'absolute',
                    right: '12px',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    border: 'none',
                    background: 'none',
                    cursor: 'pointer',
                    color: '#9ca3af',
                    display: 'flex',
                    alignItems: 'center'
                  }}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            {/* 记住我 & 忘记密码 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer', color: '#4b5563' }}>
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  style={{ width: '15px', height: '15px', margin: 0, cursor: 'pointer' }}
                />
                记住账号
              </label>
              <a href="#" onClick={(e) => { e.preventDefault(); alert("请联系系统管理员重置密码！"); }} style={{ color: '#6C3FF5', textDecoration: 'none', fontWeight: 500 }}>
                忘记密码?
              </a>
            </div>

            {/* 错误显示 */}
            {error && (
              <div style={{
                padding: '10px 12px',
                fontSize: '13px',
                color: '#ef4444',
                backgroundColor: '#fef2f2',
                border: '1px solid #fee2e2',
                borderRadius: '6px'
              }}>
                {error}
              </div>
            )}

            {/* 登录按钮 */}
            <button
              type="submit"
              disabled={loading}
              className="btn"
              style={{
                height: '46px',
                backgroundColor: '#6C3FF5',
                color: '#fff',
                border: 'none',
                borderRadius: '8px',
                fontWeight: 600,
                fontSize: '15px',
                cursor: 'pointer',
                transition: 'background-color 0.2s',
                marginTop: '6px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              {loading ? "登录中..." : "登录"}
            </button>
          </form>

          {/* 微信快速登录 */}
          <div style={{ marginTop: '16px' }}>
            <button
              onClick={() => { window.location.href = getWeChatOAuthUrl(); }}
              style={{
                width: '100%',
                height: '46px',
                backgroundColor: '#ffffff',
                border: '1px solid #e5e7eb',
                borderRadius: '8px',
                color: '#4b5563',
                fontSize: '14px',
                fontWeight: 500,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px'
              }}
            >
              <MessageCircle size={18} color="#07C160" />
              微信扫码快捷登录
            </button>
          </div>

          {/* 注册跳转 */}
          <div style={{ textAlign: 'center', marginTop: '24px', fontSize: '14px', color: '#6b7280' }}>
            还没有账号?{" "}
            <Link to="/register" style={{ color: '#6C3FF5', fontWeight: 600, textDecoration: 'none' }}>
              立即注册
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
